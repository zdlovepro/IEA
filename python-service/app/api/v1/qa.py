from __future__ import annotations

import json

from fastapi import APIRouter, Query
from fastapi.responses import StreamingResponse

from app.schemas.common import BaseResponse, success_response
from app.schemas.qa import QaAskTextRequest
from app.services.rag_service import answer_question, stream_answer_events
from app.utils.logger import logger

router = APIRouter(prefix="/qa", tags=["qa"])


@router.post("/ask-text", response_model=BaseResponse, summary="Ask text question with RAG")
async def ask_text_endpoint(request: QaAskTextRequest) -> BaseResponse:
    logger.info(
        "QA request received. sessionId=%s coursewareId=%s pageIndex=%s topK=%s",
        request.session_id,
        request.courseware_id,
        request.page_index,
        request.top_k,
    )
    return success_response(answer_question(request).model_dump(by_alias=True))


@router.get("/stream", summary="Stream QA answer with SSE")
async def stream_qa_endpoint(
    courseware_id: str = Query(..., alias="coursewareId", min_length=1),
    question: str = Query(..., min_length=1),
    session_id: str | None = Query(default=None, alias="sessionId"),
    page_index: int | None = Query(default=None, alias="pageIndex", ge=1),
    top_k: int = Query(default=5, alias="topK", ge=1, le=10),
) -> StreamingResponse:
    request = QaAskTextRequest(
        sessionId=session_id or "stream_session",
        coursewareId=courseware_id,
        pageIndex=page_index,
        question=question,
        topK=top_k,
    )

    logger.info(
        "QA stream request received. sessionId=%s coursewareId=%s pageIndex=%s topK=%s",
        request.session_id,
        request.courseware_id,
        request.page_index,
        request.top_k,
    )

    async def event_stream():
        try:
            for event in stream_answer_events(request):
                yield _format_sse(event)
        except Exception as exc:  # noqa: BLE001
            logger.warning(
                "QA stream failed. sessionId=%s coursewareId=%s reason=%s",
                request.session_id,
                request.courseware_id,
                str(exc),
            )
            yield _format_sse({"type": "error", "message": "当前问答服务暂时不可用，请稍后重试。"})
        finally:
            yield _format_sse({"type": "done"})

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        },
    )


def _format_sse(payload: dict[str, str]) -> str:
    return f"data: {json.dumps(payload, ensure_ascii=False)}\n\n"
