"""
讲稿生成服务 —— Prompt 工程核心模块。

职责：
  1. 将课件解析结果组装为结构化 Prompt；
  2. 调用 LLM 生成包含开场白、页面讲解、过渡语、结语的完整讲稿；
  3. 对模型输出做 JSON 解析与 Pydantic 强校验；
  4. 所有异常统一兜底，不暴露原始模型输出。
"""
import json
import logging
import re
from typing import Dict, Any

from langchain.prompts import ChatPromptTemplate, SystemMessagePromptTemplate, HumanMessagePromptTemplate
from pydantic import ValidationError

from app.clients.llm_client import get_llm_client
from app.schemas.script import (
    ScriptGenerateRequest,
    ScriptGenerateResponse,
    PageScript,
)

logger = logging.getLogger(__name__)

# ──────────────────────────────────────────────────────────────────
# Prompt 模板定义
# ──────────────────────────────────────────────────────────────────

# 系统角色提示：定义 AI 讲师的角色、风格和输出约束
_SYSTEM_TEMPLATE = """\
你是一位经验丰富的教育领域 AI 讲师助手，擅长将课件内容转化为自然流畅、生动易懂的口语化讲稿。
生成的讲稿将直接用于 AI 数字人朗读，须满足以下要求：

【角色与风格】
- 语气亲切、专业，贴近真实课堂授课风格；
- 避免书面化长句，优先使用短句、举例、类比；
- 重点知识点需着重强调，帮助学生理解和记忆。

【结构要求】
- 开场白（opening）：热情介绍课件主题与本节学习目标，约 100-150 字；
- 每页讲解（pages[].script）：聚焦本页核心知识点，配合说明与举例，约 150-300 字；
- 过渡语（pages[].transition）：自然衔接当前页与下一页，起承上启下作用，约 30-50 字；
  最后一页的 transition 为本页内容的小结语，约 30-50 字；
- 结语（closing）：总结本节重点，鼓励学生，约 50-80 字。

【输出约束 —— 极其重要】
- 必须且只能输出合法的 JSON，不得包含任何 Markdown 代码块标记（```）、注释或解释；
- 严格遵守下方 JSON Schema，字段不得缺失、不得新增；
- 字符串值中若含引号需转义。
"""

# JSON Schema 示例（注入到用户提示中，引导输出格式）
_JSON_SCHEMA_EXAMPLE = """\
{
  "courseware_id": "<与请求中一致>",
  "opening": "<整节课开场白，字符串>",
  "pages": [
    {
      "page_index": 1,
      "script": "<第1页讲解内容，字符串>",
      "transition": "<过渡到第2页的衔接语，字符串>"
    },
    {
      "page_index": 2,
      "script": "<第2页讲解内容，字符串>",
      "transition": "<最后一页小结语，字符串>"
    }
  ],
  "closing": "<整节课结语，字符串>"
}"""

# 用户消息模板：传入课件元信息和各页内容
_HUMAN_TEMPLATE = """\
请根据以下课件信息生成完整结构化讲稿。

【课件信息】
课件 ID：{courseware_id}
课件名称：{courseware_name}
学科方向：{subject}
总页数：{total_pages}

【各页内容】
{pages_content}

【输出 JSON Schema（严格遵守）】
{json_schema}

现在请输出 JSON："""


def _build_prompt_template() -> ChatPromptTemplate:
    """构建 ChatPromptTemplate，仅初始化一次。"""
    return ChatPromptTemplate.from_messages([
        SystemMessagePromptTemplate.from_template(_SYSTEM_TEMPLATE),
        HumanMessagePromptTemplate.from_template(_HUMAN_TEMPLATE),
    ])


_PROMPT_TEMPLATE = _build_prompt_template()


# ──────────────────────────────────────────────────────────────────
# 辅助函数
# ──────────────────────────────────────────────────────────────────

def _format_pages_content(request: ScriptGenerateRequest) -> str:
    """将各页内容序列化为简洁的文本块，便于 Prompt 理解。"""
    lines = []
    for page in request.pages:
        lines.append(f"--- 第 {page.page_index} 页 ---")
        if page.title:
            lines.append(f"标题：{page.title}")
        lines.append(f"正文：{page.text_content.strip()}")
        if page.keywords:
            lines.append(f"关键词：{'、'.join(page.keywords)}")
    return "\n".join(lines)


def _extract_json_from_response(raw: str) -> str:
    """
    从模型原始输出中提取 JSON 字符串。
    处理模型可能错误包裹的 Markdown 代码块。
    """
    # 尝试剥离 ```json ... ``` 或 ``` ... ```
    match = re.search(r"```(?:json)?\s*([\s\S]*?)```", raw, re.IGNORECASE)
    if match:
        return match.group(1).strip()
    # 尝试提取第一个 { ... } 大括号块
    match = re.search(r"(\{[\s\S]*\})", raw)
    if match:
        return match.group(1).strip()
    return raw.strip()


def _parse_and_validate(raw_output: str, courseware_id: str) -> ScriptGenerateResponse:
    """
    解析模型输出的 JSON 并用 Pydantic 做强校验。
    任何格式错误均向上抛出，由调用方做兜底。
    """
    json_str = _extract_json_from_response(raw_output)
    data: Dict[str, Any] = json.loads(json_str)

    # 确保 courseware_id 与请求一致
    data["courseware_id"] = courseware_id

    validated = ScriptGenerateResponse(**data)
    return validated


# ──────────────────────────────────────────────────────────────────
# 公开服务函数
# ──────────────────────────────────────────────────────────────────

def generate_script(request: ScriptGenerateRequest) -> Dict[str, Any]:
    """
    生成结构化讲稿的核心服务函数。

    返回：
      成功 -> {"code": 0, "message": "success", "data": ScriptGenerateResponse.dict()}
      失败 -> {"code": 50001, "message": "<错误描述>", "data": None}
    """
    logger.info(
        "开始生成讲稿 | courseware_id=%s | pages=%d",
        request.courseware_id,
        len(request.pages),
    )

    # 1. 组装 Prompt 变量
    pages_content = _format_pages_content(request)
    subject_str = request.subject or "通用课程"

    prompt_messages = _PROMPT_TEMPLATE.format_messages(
        courseware_id=request.courseware_id,
        courseware_name=request.courseware_name,
        subject=subject_str,
        total_pages=len(request.pages),
        pages_content=pages_content,
        json_schema=_JSON_SCHEMA_EXAMPLE,
    )

    # 2. 调用大模型
    try:
        llm_client = get_llm_client()
        raw_output = llm_client.invoke(prompt_messages)
    except Exception as exc:
        logger.error("大模型调用异常 | courseware_id=%s | error=%s", request.courseware_id, exc)
        return {"code": 50001, "message": f"大模型服务异常：{type(exc).__name__}", "data": None}

    # 3. 解析与校验
    try:
        result = _parse_and_validate(raw_output, request.courseware_id)
        logger.info("讲稿生成成功 | courseware_id=%s", request.courseware_id)
        return {"code": 0, "message": "success", "data": result.model_dump()}
    except json.JSONDecodeError as exc:
        logger.error(
            "大模型输出 JSON 解析失败 | courseware_id=%s | error=%s | raw=%s",
            request.courseware_id, exc, raw_output[:200],
        )
        return {"code": 50001, "message": "大模型输出格式异常：JSON 解析失败", "data": None}
    except ValidationError as exc:
        logger.error(
            "大模型输出字段校验失败 | courseware_id=%s | error=%s",
            request.courseware_id, exc,
        )
        return {"code": 50001, "message": "大模型输出格式异常：字段校验失败", "data": None}
