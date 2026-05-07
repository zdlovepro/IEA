from __future__ import annotations

from pathlib import Path
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class BaseResponse(BaseModel):
    code: int = 0
    message: str = "success"
    data: Any | None = None


def success_response(data: Any = None) -> BaseResponse:
    return BaseResponse(code=0, message="success", data=data)


def error_response(code: int, message: str) -> BaseResponse:
    return BaseResponse(code=code, message=message, data=None)


class PageContent(BaseModel):
    page_index: int = Field(..., ge=1)
    text: str = ""
    notes: str = ""
    image_placeholders: list[str] = Field(default_factory=list)
    formula_placeholders: list[str] = Field(default_factory=list)


class ParseResult(BaseModel):
    courseware_id: str
    file_type: str
    total_pages: int = Field(..., ge=0)
    pages: list[PageContent] = Field(default_factory=list)


class ParseRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    courseware_id: str = Field(..., alias="coursewareId")
    storage: str | None = None
    key: str | None = None
    file_name: str | None = Field(default=None, alias="fileName")
    content_type: str | None = Field(default=None, alias="contentType")

    # Legacy compatibility fields. New requests should use storage/key/fileName/contentType.
    file_url: str | None = Field(default=None, alias="fileUrl")
    file_path: str | None = Field(default=None, alias="filePath")

    @property
    def normalized_storage(self) -> str:
        return (self.storage or "local").strip().lower()

    @property
    def preferred_name(self) -> str | None:
        if self.file_name:
            return self.file_name
        if self.key:
            return Path(self.key).name
        if self.file_path:
            return Path(self.file_path).name
        if self.file_url:
            return Path(self.file_url).name
        return None
