from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class QaAskTextRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    session_id: str = Field(..., min_length=1, alias="sessionId")
    courseware_id: str = Field(..., min_length=1, alias="coursewareId")
    page_index: int | None = Field(default=None, ge=1, alias="pageIndex")
    question: str = Field(..., min_length=1)
    top_k: int = Field(default=5, ge=1, le=10, alias="topK")


class QaEvidenceItem(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    source: str
    text: str
    page_index: int | None = Field(default=None, alias="pageIndex")
    chunk_id: str | None = Field(default=None, alias="chunkId")


class QaAskTextResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True, extra="ignore")

    answer: str = Field(..., min_length=1)
    evidence: list[QaEvidenceItem] = Field(default_factory=list)
    latency_ms: int = Field(..., ge=0, alias="latencyMs")
