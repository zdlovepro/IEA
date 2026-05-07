from __future__ import annotations

from fastapi import APIRouter

from app.schemas.parse import BaseResponse
from app.schemas.script import ScriptGenerateRequest
from app.utils.logger import logger

router = APIRouter(prefix="/script", tags=["script"])


@router.post(
    "/generate",
    response_model=BaseResponse,
    summary="Generate lecture script",
)
async def generate_script_endpoint(request: ScriptGenerateRequest) -> BaseResponse:
    logger.info("Received script generation request for courseware_id=%s", request.courseware_id)

    from app.services.script_service import generate_script

    result = generate_script(request)
    return BaseResponse(**result)
