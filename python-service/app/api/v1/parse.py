from __future__ import annotations

from fastapi import APIRouter

from app.schemas.common import BaseResponse, success_response
from app.schemas.parse import ParseRequest
from app.services.parse_service import parse_courseware_file

router = APIRouter(tags=["parse"])


@router.post("/parse", response_model=BaseResponse, summary="Parse courseware")
async def parse_courseware(request: ParseRequest) -> BaseResponse:
    return success_response(parse_courseware_file(request))
