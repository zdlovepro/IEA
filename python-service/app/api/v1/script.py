from __future__ import annotations

from fastapi import APIRouter

from app.schemas.common import BaseResponse, success_response
from app.schemas.script import ScriptGenerateRequest
from app.services.script_service import generate_script
from app.utils.logger import logger

router = APIRouter(prefix="/script", tags=["script"])


@router.post("/generate", response_model=BaseResponse, summary="Generate lecture script")
async def generate_script_endpoint(request: ScriptGenerateRequest) -> BaseResponse:
    logger.info("Script generation request received. coursewareId=%s", request.courseware_id)
    return success_response(generate_script(request).model_dump())
