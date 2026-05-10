from __future__ import annotations

import time

from langchain.schema import HumanMessage, SystemMessage

from app.clients.llm_client import get_llm_client
from app.core.config import settings
from app.schemas.qa import QaAskTextRequest, QaAskTextResponse, QaEvidenceItem
from app.services.rag_retriever import retrieve_context
from app.utils.logger import logger

_SYSTEM_PROMPT = """\
你是课堂中的 AI 助教。
你只能根据给定的课件 evidence 回答问题，不能补充课件之外的事实、推测、背景知识或案例。
如果 evidence 不足，请明确回答：“课件中没有直接覆盖该内容”。
回答要简洁、口语化，适合课堂中直接朗读。
不要编造课件没有的信息。"""


def answer_question(request: QaAskTextRequest) -> QaAskTextResponse:
    start_at = time.perf_counter()
    evidence_context = retrieve_context(
        courseware_id=request.courseware_id,
        question=request.question,
        page_index=request.page_index,
        top_k=request.top_k,
    )

    if not evidence_context:
        answer = "课件中没有直接覆盖该内容。你可以换个更贴近当前页的问题，或者告诉我想确认哪一页。"
        return _build_response(answer, evidence_context, start_at)

    if not settings.LLM_API_KEY:
        logger.info(
            "LLM API key missing for QA. Use evidence template answer. coursewareId=%s sessionId=%s evidenceCount=%s",
            request.courseware_id,
            request.session_id,
            len(evidence_context),
        )
        answer = _build_template_answer(request.question, evidence_context)
        return _build_response(answer, evidence_context, start_at)

    try:
        messages = [
            SystemMessage(content=_SYSTEM_PROMPT),
            HumanMessage(content=_build_user_prompt(request.question, request.page_index, evidence_context)),
        ]
        answer = (get_llm_client().invoke(messages) or "").strip()
        if not answer:
            answer = _build_template_answer(request.question, evidence_context)
        logger.info(
            "RAG QA answered by LLM. coursewareId=%s sessionId=%s evidenceCount=%s",
            request.courseware_id,
            request.session_id,
            len(evidence_context),
        )
        return _build_response(answer, evidence_context, start_at)
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "RAG QA degraded to template answer. coursewareId=%s sessionId=%s reason=%s",
            request.courseware_id,
            request.session_id,
            str(exc),
        )
        answer = _build_template_answer(request.question, evidence_context)
        return _build_response(answer, evidence_context, start_at)


def _build_user_prompt(question: str, page_index: int | None, evidence_context: list[dict]) -> str:
    lines = [
        f"课堂提问：{question.strip()}",
        f"当前页：{page_index if page_index is not None else '未知'}",
        "可用证据：",
    ]

    for index, item in enumerate(evidence_context, start=1):
        lines.append(
            f"{index}. 来源={item.get('source', 'courseware')} | 页码={item.get('page_index')} | "
            f"得分={item.get('adjusted_score', item.get('score', 0.0)):.2f}"
        )
        lines.append(f"证据内容：{_trim_text(item.get('text', ''))}")

    lines.append("请只依据这些证据作答；如果证据不够，请直接回答“课件中没有直接覆盖该内容”。")
    return "\n".join(lines)


def _build_template_answer(question: str, evidence_context: list[dict]) -> str:
    primary_evidence = evidence_context[0] if evidence_context else {}
    page_index = primary_evidence.get("page_index")
    text = _trim_text(primary_evidence.get("text", ""), limit=140)

    if not text:
        return "课件中没有直接覆盖该内容。你可以换个更贴近当前课件的问题继续问我。"

    if page_index:
        return (
            f"根据课件第{page_index}页，这个问题可以先这样理解：{text}"
            "如果你愿意，我可以继续帮你把这一页的重点再拆得更清楚一点。"
        )

    return (
        f"根据当前课件内容，可以先这样回答：{text}"
        "如果你想更聚焦一点，也可以告诉我你想确认哪一页。"
    )


def _build_response(answer: str, evidence_context: list[dict], start_at: float) -> QaAskTextResponse:
    latency_ms = max(1, int((time.perf_counter() - start_at) * 1000))
    evidence = [
        QaEvidenceItem(
            source=str(item.get("source") or "courseware"),
            text=str(item.get("text") or ""),
            page_index=_safe_page_index(item.get("page_index")),
            chunk_id=str(item.get("chunk_id")) if item.get("chunk_id") else None,
        )
        for item in evidence_context
        if str(item.get("text") or "").strip()
    ]
    return QaAskTextResponse(
        answer=answer.strip(),
        evidence=evidence,
        latency_ms=latency_ms,
    )


def _trim_text(text: str, limit: int = 220) -> str:
    normalized = " ".join(str(text or "").split()).strip()
    if len(normalized) <= limit:
        return normalized
    return normalized[:limit].rstrip() + "..."


def _safe_page_index(page_index: object) -> int | None:
    try:
        resolved = int(page_index)
    except (TypeError, ValueError):
        return None
    return resolved if resolved > 0 else None
