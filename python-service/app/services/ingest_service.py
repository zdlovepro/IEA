from __future__ import annotations

from typing import Any

from app.services.vector_store import get_vector_store
from app.utils.logger import logger


def ingest_courseware_chunks(courseware_id: str, chunks: list[dict[str, Any]]) -> dict[str, Any]:
    if not chunks:
        logger.info("Skip ingest because chunks are empty. coursewareId=%s", courseware_id)
        return {
            "courseware_id": courseware_id,
            "inserted": 0,
            "backend": get_vector_store().backend_name,
        }

    vector_store = get_vector_store()
    inserted = vector_store.insert_documents(courseware_id, chunks)
    logger.info(
        "Courseware chunks ingested. coursewareId=%s inserted=%s backend=%s",
        courseware_id,
        inserted,
        vector_store.backend_name,
    )
    return {
        "courseware_id": courseware_id,
        "inserted": inserted,
        "backend": vector_store.backend_name,
    }
