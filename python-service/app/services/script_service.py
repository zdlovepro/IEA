from __future__ import annotations

import json
import re
from typing import Any

from langchain.prompts import ChatPromptTemplate, HumanMessagePromptTemplate, SystemMessagePromptTemplate
from pydantic import ValidationError

from app.clients.llm_client import get_llm_client
from app.core.config import settings
from app.core.exceptions import AppException, ModelOutputException
from app.schemas.script import PageScript, ScriptGenerateRequest, ScriptGenerateResponse
from app.utils.logger import logger

_SYSTEM_TEMPLATE = """\
你是一位教育领域的 AI 讲师，擅长把课件内容改写成适合教学场景朗读的结构化讲稿。
这份讲稿会直接用于 TTS 和 AI 数字人朗读，因此表达必须自然、口语化、清晰顺畅，像老师在面对学生讲解，而不是书面材料堆砌。
你必须忠实依据课件内容生成讲稿，不得编造课件中没有出现的知识点、案例、数据、结论或背景信息。
你只能输出合法 JSON，不能输出 Markdown、代码块、解释、提示语、前后缀文字或任何额外说明。"""

_JSON_SCHEMA_EXAMPLE = """\
{
  "courseware_id": "<与请求一致>",
  "opening": "<开场白>",
  "pages": [
    {
      "page_index": 1,
      "script": "<本页讲解内容>",
      "transition": "<衔接到下一页或自然收束的过渡语>"
    }
  ],
  "closing": "<结语>"
}"""

_HUMAN_TEMPLATE = """\
请根据以下课件内容，生成一份适合直接朗读的结构化讲稿。

课件 ID：{courseware_id}
课件名称：{courseware_name}
学科：{subject}
总页数：{total_pages}

生成要求：
1. 按页生成讲解稿，pages 数量必须与课件页数一致，page_index 必须与原始页码一致。
2. 每页 script 都要结合该页标题、正文和关键词来组织讲解，适合口头教学和连续朗读。
3. opening、每页 script、transition、closing 必须保持统一口吻，像同一位老师在完整授课。
4. 只讲课件中已经提供的信息，不要补充课件里没有出现的事实、案例、推论或知识背景。
5. 如果某一页正文很少或为空，可以根据标题和关键词做友好兜底，但仍然不能编造事实。
6. transition 要自然承接上下文；最后一页的 transition 也要自然收束，不能突兀。

课件逐页内容：
{pages_content}

请严格按以下 JSON 结构输出，不要输出任何额外内容：
{json_schema}
"""

_PROMPT_TEMPLATE = ChatPromptTemplate.from_messages(
    [
        SystemMessagePromptTemplate.from_template(_SYSTEM_TEMPLATE),
        HumanMessagePromptTemplate.from_template(_HUMAN_TEMPLATE),
    ]
)


def generate_script(request: ScriptGenerateRequest) -> ScriptGenerateResponse:
    total_pages = len(request.pages)
    logger.info("Script generation started. coursewareId=%s pages=%s", request.courseware_id, total_pages)

    if not settings.LLM_API_KEY:
        logger.warning(
            "LLM API key is missing. Use fallback script. coursewareId=%s pages=%s",
            request.courseware_id,
            total_pages,
        )
        return build_fallback_script(request)

    raw_output = ""
    try:
        prompt_messages = _PROMPT_TEMPLATE.format_messages(
            courseware_id=request.courseware_id,
            courseware_name=request.courseware_name,
            subject=_resolve_subject(request.subject),
            total_pages=total_pages,
            pages_content=_format_pages_content(request),
            json_schema=_JSON_SCHEMA_EXAMPLE,
        )
        raw_output = get_llm_client().invoke(prompt_messages) or ""
        result = parse_model_output(raw_output, request.courseware_id)
        logger.info("Script generation completed. coursewareId=%s pages=%s", request.courseware_id, len(result.pages))
        return result
    except ModelOutputException as exc:
        logger.warning(
            "Model output invalid, fallback applied. coursewareId=%s pages=%s reason=%s preview=%r",
            request.courseware_id,
            total_pages,
            str(exc),
            _truncate_for_log(raw_output),
        )
    except AppException as exc:
        logger.warning(
            "Script generation degraded to fallback. coursewareId=%s pages=%s code=%s",
            request.courseware_id,
            total_pages,
            exc.code,
        )
    except Exception:  # noqa: BLE001
        logger.exception("Unexpected script generation failure. coursewareId=%s pages=%s", request.courseware_id, total_pages)

    return build_fallback_script(request)


def extract_json_payload(raw_output: str) -> str:
    stripped = (raw_output or "").strip()
    if not stripped:
        return stripped

    fenced_match = re.search(r"```(?:[a-zA-Z0-9_-]+)?\s*([\s\S]*?)```", stripped)
    if fenced_match:
        fenced_content = fenced_match.group(1).strip()
        return _extract_first_json_object(fenced_content) or fenced_content

    return _extract_first_json_object(stripped) or stripped


def parse_model_output(raw_output: str, courseware_id: str) -> ScriptGenerateResponse:
    json_payload = extract_json_payload(raw_output)
    try:
        data = json.loads(json_payload)
    except json.JSONDecodeError as exc:
        raise ModelOutputException("模型输出不是合法 JSON") from exc

    if not isinstance(data, dict):
        raise ModelOutputException("模型输出 JSON 顶层必须是 object")

    normalized = _normalize_model_response_data(data, courseware_id)
    try:
        return ScriptGenerateResponse(**normalized)
    except ValidationError as exc:
        raise ModelOutputException("模型输出字段校验失败") from exc


def build_fallback_script(request: ScriptGenerateRequest) -> ScriptGenerateResponse:
    total_pages = len(request.pages)
    subject = _resolve_subject(request.subject)
    page_scripts: list[PageScript] = []

    for index, page in enumerate(request.pages, start=1):
        title = _resolve_title(page.title, page.page_index)
        condensed_text = _condense_text(page.text_content)
        keywords_text = _format_keywords(page.keywords)
        next_title = request.pages[index].title if index < total_pages else None
        transition = _build_transition(index, total_pages, next_title)

        if condensed_text:
            script = (
                f"现在我们来看第{page.page_index}页，主题是“{title}”。"
                f"这一页主要在讲：{condensed_text}。"
                f"你可以重点留意{keywords_text}，这样更容易把这一页的核心意思抓住。"
            )
        else:
            script = (
                f"现在我们来看第{page.page_index}页，主题是“{title}”。"
                "这一页的原始文字比较少，我们先结合标题和关键词来理解它要表达的重点。"
                f"建议你重点关注{keywords_text}，再结合课件画面一起把意思串起来。"
            )

        page_scripts.append(
            PageScript(
                page_index=page.page_index,
                script=script,
                transition=transition,
            )
        )

    response = ScriptGenerateResponse(
        courseware_id=request.courseware_id,
        opening=(
            f"同学们好，接下来我们一起学习《{request.courseware_name}》。"
            f"这是一节{subject}相关内容，我会按页带大家梳理重点，帮助你更轻松地听懂整份课件。"
        ),
        pages=page_scripts,
        closing=(
            "这份课件的主要内容就梳理到这里了。"
            "建议你回看每一页的核心概念和关键词，再结合课件中的例子复习一遍，这样理解会更扎实。"
        ),
    )
    logger.info("Fallback script built. coursewareId=%s pages=%s", request.courseware_id, len(response.pages))
    return response


def _format_pages_content(request: ScriptGenerateRequest) -> str:
    total_pages = len(request.pages)
    lines: list[str] = []
    for page in request.pages:
        lines.append(f"--- 第 {page.page_index} 页 / 共 {total_pages} 页 ---")
        lines.append(f"标题：{_resolve_title(page.title, page.page_index)}")
        lines.append(f"正文：{_clean_text(page.text_content) or '（本页正文为空）'}")
        lines.append(f"关键词：{_format_keywords(page.keywords)}")
    return "\n".join(lines)


def _normalize_model_response_data(data: dict[str, Any], courseware_id: str) -> dict[str, Any]:
    pages = data.get("pages")
    if not isinstance(pages, list) or not pages:
        raise ModelOutputException("模型输出缺少有效的 pages 列表")

    normalized_pages: list[dict[str, Any]] = []
    total_pages = len(pages)
    for index, page in enumerate(pages, start=1):
        if not isinstance(page, dict):
            raise ModelOutputException("模型输出中的 page 项必须是 object")

        page_index = _parse_page_index(page.get("page_index"))
        script = _clean_text(str(page.get("script", "")))
        if not script:
            raise ModelOutputException("模型输出中的 script 不能为空")

        # 当模型漏掉 transition 时，这里补一个默认过渡语，优先保证整份讲稿仍可朗读。
        transition = _clean_text(str(page.get("transition", ""))) or _build_transition(
            index,
            total_pages,
            None,
        )

        normalized_pages.append(
            {
                "page_index": page_index,
                "script": script,
                "transition": transition,
            }
        )

    return {
        "courseware_id": courseware_id,
        "opening": _clean_text(str(data.get("opening", ""))),
        "pages": normalized_pages,
        "closing": _clean_text(str(data.get("closing", ""))),
    }


def _parse_page_index(raw_value: Any) -> int:
    try:
        page_index = int(raw_value)
    except (TypeError, ValueError) as exc:
        raise ModelOutputException("模型输出中的 page_index 必须是正整数") from exc

    if page_index <= 0:
        raise ModelOutputException("模型输出中的 page_index 必须是正整数")
    return page_index


def _extract_first_json_object(text: str) -> str | None:
    start_index = -1
    depth = 0
    in_string = False
    escaped = False

    for index, char in enumerate(text):
        if start_index < 0:
            if char == "{":
                start_index = index
                depth = 1
            continue

        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue

        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start_index : index + 1].strip()

    return None


def _truncate_for_log(raw_output: str, limit: int = 200) -> str:
    normalized = (raw_output or "").strip()
    if len(normalized) <= limit:
        return normalized
    return normalized[:limit].rstrip() + "..."


def _resolve_subject(subject: str | None) -> str:
    normalized = _clean_text(subject or "")
    return normalized or "通用课程"


def _resolve_title(title: str | None, page_index: int) -> str:
    normalized = _clean_text(title or "")
    return normalized or f"第{page_index}页重点"


def _format_keywords(keywords: list[str]) -> str:
    cleaned = [_clean_text(keyword) for keyword in keywords if _clean_text(keyword)]
    if not cleaned:
        return "本页核心概念"
    return "、".join(cleaned)


def _build_transition(index: int, total_pages: int, next_title: str | None) -> str:
    if index >= total_pages:
        return "这一页的重点我们先整理到这里，接下来我会带你一起做一个整体回顾。"

    resolved_next_title = _clean_text(next_title or "")
    if resolved_next_title:
        return f"理解了这一页之后，我们继续看看下一页“{resolved_next_title}”，把前后的思路顺着连起来。"
    return "理解了这一页之后，我们继续看下一页，把接下来的重点内容自然串起来。"


def _clean_text(text: str) -> str:
    return " ".join(text.split()).strip()


def _condense_text(text: str, limit: int = 220) -> str:
    normalized = _clean_text(text)
    if not normalized:
        return ""
    return normalized if len(normalized) <= limit else normalized[:limit].rstrip() + "..."
