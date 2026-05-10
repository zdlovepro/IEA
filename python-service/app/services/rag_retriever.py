from __future__ import annotations

from typing import Any

from app.services.vector_store import get_vector_store
from app.utils.logger import logger

_CURRENT_PAGE_BONUS = 0.30
_ADJACENT_PAGE_BONUS = 0.15
_CANDIDATE_MULTIPLIER = 3


def retrieve_context(
    courseware_id: str,
    question: str,
    page_index: int | None = None,
    top_k: int = 5,
) -> list[dict[str, Any]]:
    normalized_question = (question or "").strip()
    if not courseware_id or not normalized_question or top_k <= 0:
        return []

    candidate_limit = max(top_k * _CANDIDATE_MULTIPLIER, top_k)

    try:
        candidates = get_vector_store().search_similar(
            query=normalized_question,
            courseware_id=courseware_id,
            top_k=candidate_limit,
        )
    except Exception as exc:  # noqa: BLE001
        logger.warning(
            "RAG retrieval failed. coursewareId=%s pageIndex=%s topK=%s reason=%s",
            courseware_id,
            page_index,
            top_k,
            str(exc),
        )
        return []

    if not candidates:
        return []

    weighted_results: list[dict[str, Any]] = []
    for rank, candidate in enumerate(candidates):
        resolved_page_index = _resolve_page_index(candidate)
        base_score = _resolve_base_score(candidate)
        adjusted_score = base_score + _page_bonus(resolved_page_index, page_index)
        text = _resolve_text(candidate)
        metadata = candidate.get("metadata") if isinstance(candidate.get("metadata"), dict) else {}

        weighted_results.append(
            {
                "chunk_id": str(candidate.get("chunk_id") or candidate.get("id") or ""),
                "page_index": resolved_page_index,
                "text": text,
                "source": _build_source(resolved_page_index),
                "score": base_score,
                "adjusted_score": adjusted_score,
                "metadata": metadata,
                "_rank": rank,
            }
        )

    weighted_results.sort(
        key=lambda item: (item["adjusted_score"], item["score"], -item["_rank"]),
        reverse=True,
    )

    final_results = weighted_results[:top_k]
    for item in final_results:
        item.pop("_rank", None)
    return final_results


def _resolve_page_index(candidate: dict[str, Any]) -> int | None:
    page_index = candidate.get("page_index")
    if page_index in (None, "", 0):
        metadata = candidate.get("metadata")
        if isinstance(metadata, dict):
            page_index = metadata.get("page_index")

    try:
        resolved_page_index = int(page_index)
    except (TypeError, ValueError):
        return None

    return resolved_page_index if resolved_page_index > 0 else None


def _resolve_base_score(candidate: dict[str, Any]) -> float:
    raw_score = candidate.get("score", candidate.get("distance", 0.0))
    try:
        return float(raw_score)
    except (TypeError, ValueError):
        return 0.0


def _resolve_text(candidate: dict[str, Any]) -> str:
    return str(candidate.get("content") or candidate.get("text") or "").strip()


def _build_source(page_index: int | None) -> str:
    return f"page_{page_index}" if page_index is not None else "courseware"


def _page_bonus(candidate_page_index: int | None, current_page_index: int | None) -> float:
    if candidate_page_index is None or current_page_index is None:
        return 0.0
    if candidate_page_index == current_page_index:
        return _CURRENT_PAGE_BONUS
    if abs(candidate_page_index - current_page_index) == 1:
        return _ADJACENT_PAGE_BONUS
    return 0.0
