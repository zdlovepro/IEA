from __future__ import annotations

from typing import Any

from pydantic import BaseModel


class BaseResponse(BaseModel):
    code: int = 0
    message: str = "success"
    data: Any | None = None


def success_response(data: Any = None) -> BaseResponse:
    return BaseResponse(code=0, message="success", data=data)


def error_response(code: int, message: str) -> BaseResponse:
    return BaseResponse(code=code, message=message, data=None)
