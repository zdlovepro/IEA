from __future__ import annotations

import json
import re

from langchain.prompts import ChatPromptTemplate, HumanMessagePromptTemplate, SystemMessagePromptTemplate
from pydantic import ValidationError

from app.clients.llm_client import get_llm_client
from app.core.config import settings
from app.core.exceptions import AppException, ModelOutputException
from app.schemas.script import PageScript, ScriptGenerateRequest, ScriptGenerateResponse
from app.utils.logger import logger

_SYSTEM_TEMPLATE = """\
你是一位经验丰富的教育领域 AI 讲师助手，擅长将课件内容转化为自然流畅、生动易懂的口语化讲稿。
生成的讲稿将直接用于 AI 数字人朗读，必须只输出合法 JSON，不要输出 Markdown 代码块、解释或额外说明。
"""

_JSON_SCHEMA_EXAMPLE = """\
{
  "courseware_id": "<与请求一致>",
  "opening": "<开场白>",
  "pages": [
    {
      "page_index": 1,
      "script": "<本页讲解内容>",
      "transition": "<下一页过渡语>"
    }
  ],
  "closing": "<结语>"
}"""

_HUMAN_TEMPLATE = """\
请根据以下课件内容生成完整讲稿。

课件 ID：{courseware_id}
课件名称：{courseware_name}
学科：{subject}
总页数：{total_pages}

页面内容：
{pages_content}

请严格按照以下 JSON Schema 输出：
{json_schema}
"""

_PROMPT_TEMPLATE = ChatPromptTemplate.from_messages(
    [
        SystemMessagePromptTemplate.from_template(_SYSTEM_TEMPLATE),
        HumanMessagePromptTemplate.from_template(_HUMAN_TEMPLATE),
    ]
)


def generate_script(request: ScriptGenerateRequest) -> ScriptGenerateResponse:
    logger.info("Script generation started. coursewareId=%s pages=%s", request.courseware_id, len(request.pages))

    if not settings.LLM_API_KEY:
        logger.warning("LLM API key is missing. Use fallback script. coursewareId=%s", request.courseware_id)
        return build_fallback_script(request)

    try:
        prompt_messages = _PROMPT_TEMPLATE.format_messages(
            courseware_id=request.courseware_id,
            courseware_name=request.courseware_name,
            subject=request.subject or "通用课程",
            total_pages=len(request.pages),
            pages_content=_format_pages_content(request),
            json_schema=_JSON_SCHEMA_EXAMPLE,
        )
        raw_output = get_llm_client().invoke(prompt_messages)
        result = parse_model_output(raw_output, request.courseware_id)
        logger.info("Script generation completed. coursewareId=%s", request.courseware_id)
        return result
    except (ModelOutputException, ValidationError, json.JSONDecodeError) as exc:
        logger.warning("Model output invalid, fallback applied. coursewareId=%s reason=%s", request.courseware_id, type(exc).__name__)
    except AppException as exc:
        logger.warning("Script generation degraded to fallback. coursewareId=%s code=%s", request.courseware_id, exc.code)
    except Exception:  # noqa: BLE001
        logger.exception("Unexpected script generation failure. coursewareId=%s", request.courseware_id)

    return build_fallback_script(request)


def extract_json_payload(raw_output: str) -> str:
    fenced_match = re.search(r"```(?:json)?\s*([\s\S]*?)```", raw_output, re.IGNORECASE)
    if fenced_match:
        return fenced_match.group(1).strip()

    object_match = re.search(r"(\{[\s\S]*\})", raw_output)
    if object_match:
        return object_match.group(1).strip()

    return raw_output.strip()


def parse_model_output(raw_output: str, courseware_id: str) -> ScriptGenerateResponse:
    json_payload = extract_json_payload(raw_output)
    data = json.loads(json_payload)
    data["courseware_id"] = courseware_id
    try:
        return ScriptGenerateResponse(**data)
    except ValidationError as exc:
        raise ModelOutputException("模型输出字段校验失败") from exc


def build_fallback_script(request: ScriptGenerateRequest) -> ScriptGenerateResponse:
    page_scripts: list[PageScript] = []
    total_pages = len(request.pages)
    for index, page in enumerate(request.pages, start=1):
        title = page.title or f"第{page.page_index}页"
        condensed_text = _condense_text(page.text_content)
        transition = "接下来我们继续看下一页的重点内容。" if index < total_pages else "以上就是这一页的重点内容。"
        page_scripts.append(
            PageScript(
                page_index=page.page_index,
                script=f"现在我们来看{title}。这一页主要内容是：{condensed_text}",
                transition=transition,
            )
        )

    return ScriptGenerateResponse(
        courseware_id=request.courseware_id,
        opening=f"大家好，这节课我们一起学习《{request.courseware_name}》的核心内容。",
        pages=page_scripts,
        closing="以上就是本节课的重点内容，建议结合课件再复习一遍关键知识点。",
    )


def _format_pages_content(request: ScriptGenerateRequest) -> str:
    lines: list[str] = []
    for page in request.pages:
        lines.append(f"--- 第 {page.page_index} 页 ---")
        if page.title:
            lines.append(f"标题：{page.title}")
        lines.append(f"正文：{page.text_content.strip()}")
        if page.keywords:
            lines.append(f"关键词：{'、'.join(page.keywords)}")
    return "\n".join(lines)


def _condense_text(text: str, limit: int = 180) -> str:
    normalized = " ".join(text.split())
    return normalized if len(normalized) <= limit else normalized[:limit] + "..."
