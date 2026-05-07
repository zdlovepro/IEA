"""
讲稿生成相关的 Pydantic Schema 定义。
输入/输出模型分离，所有字段带类型及描述。
"""
from typing import List, Optional
from pydantic import BaseModel, Field


# ────────────────────── 请求模型 ──────────────────────

class PageContent(BaseModel):
    """单页课件内容（由 Java 后端在解析完成后传入）"""
    page_index: int = Field(..., ge=1, description="页码，从 1 开始")
    title: Optional[str] = Field(None, description="本页标题（可空）")
    text_content: str = Field(..., description="本页提取的正文文本")
    keywords: Optional[List[str]] = Field(default_factory=list, description="本页关键词列表")


class ScriptGenerateRequest(BaseModel):
    """讲稿生成请求"""
    courseware_id: str = Field(..., description="课件唯一标识")
    courseware_name: str = Field(..., description="课件名称，用于生成开场白")
    subject: Optional[str] = Field(None, description="课件学科/方向，辅助生成风格（如：数据结构、高等数学）")
    pages: List[PageContent] = Field(..., min_length=1, description="各页内容列表，按页码升序排列")


# ────────────────────── 响应模型 ──────────────────────

class PageScript(BaseModel):
    """单页讲稿"""
    page_index: int = Field(..., description="对应页码")
    script: str = Field(..., description="该页的核心口语化讲解内容")
    transition: str = Field(..., description="过渡到下一页的衔接语（最后一页为总结语）")


class ScriptGenerateResponse(BaseModel):
    """讲稿生成结果"""
    courseware_id: str = Field(..., description="课件唯一标识")
    opening: str = Field(..., description="整节课的开场白")
    pages: List[PageScript] = Field(..., description="各页讲稿列表，与请求页码一一对应")
    closing: str = Field(..., description="整节课的结语")


class ScriptGenerateResult(BaseModel):
    """对外统一响应包装"""
    code: int = Field(0, description="0 表示成功，非 0 表示失败")
    message: str = Field("success", description="结果描述")
    data: Optional[ScriptGenerateResponse] = Field(None, description="讲稿数据，失败时为 null")
