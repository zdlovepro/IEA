from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class ChunkMetadata(BaseModel):
    courseware_id: str = Field(default="", description="Courseware identifier")
    page_index: int = Field(..., ge=1, description="1-based page index")
    chunk_index: int = Field(..., ge=0, description="0-based chunk index within the page")
    source: str = Field(default="courseware", description="Chunk source type")
    sentence_count: int = Field(..., ge=1, description="Sentence count in the chunk")
    char_count: int = Field(..., ge=1, description="Character count in the chunk")
    title: str | None = Field(default=None, description="Optional page title")
    knowledge_points: list[str] | None = Field(default=None, description="Optional knowledge points")


class TextChunk(BaseModel):
    chunk_id: str = Field(..., min_length=1, description="Stable chunk identifier")
    courseware_id: str = Field(default="", description="Courseware identifier")
    page_index: int = Field(..., ge=1, description="1-based page index")
    chunk_index: int = Field(..., ge=0, description="0-based chunk index within the page")
    text: str = Field(..., min_length=1, description="Chunk text content")
    content: str = Field(..., min_length=1, description="Chunk content alias")
    sentences: list[str] = Field(..., min_length=1, description="Natural sentences included in the chunk")
    metadata: ChunkMetadata = Field(..., description="Chunk metadata")

    @property
    def metadata_dict(self) -> dict[str, Any]:
        return self.metadata.model_dump(mode="python")
