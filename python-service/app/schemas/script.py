from __future__ import annotations

from typing import List

from pydantic import BaseModel, ConfigDict, Field


class PageContent(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    page_index: int = Field(..., ge=1, alias="pageIndex", description="页码，从 1 开始")
    title: str | None = Field(default=None, description="当前页标题")
    text_content: str = Field(..., min_length=1, alias="textContent", description="当前页正文文本")
    keywords: List[str] = Field(default_factory=list, description="当前页关键词列表")


class ScriptGenerateRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    courseware_id: str = Field(..., min_length=1, alias="coursewareId", description="课件唯一标识")
    courseware_name: str = Field(..., min_length=1, alias="coursewareName", description="课件名称")
    subject: str | None = Field(default=None, description="课件所属学科")
    pages: List[PageContent] = Field(..., min_length=1, description="按页码升序排列的页面内容")


class PageScript(BaseModel):
    page_index: int = Field(..., description="对应页码")
    script: str = Field(..., min_length=1, description="本页讲解内容")
    transition: str = Field(..., min_length=1, description="衔接到下一页的过渡语")


class ScriptGenerateResponse(BaseModel):
    courseware_id: str = Field(..., description="课件唯一标识")
    opening: str = Field(..., min_length=1, description="整节课的开场白")
    pages: List[PageScript] = Field(..., min_length=1, description="逐页讲稿列表")
    closing: str = Field(..., min_length=1, description="整节课的结语")
