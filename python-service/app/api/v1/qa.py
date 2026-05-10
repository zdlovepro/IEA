from __future__ import annotations

from fastapi import APIRouter

from app.schemas.common import BaseResponse, success_response
from app.schemas.qa import QaAskTextRequest
from app.services.rag_service import answer_question
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
