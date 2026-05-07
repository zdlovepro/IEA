from __future__ import annotations

import re
from itertools import count
from typing import Any, Dict, List

from app.core.config import settings
from app.core.exceptions import VectorStoreException
from app.utils.logger import logger

try:
    from pymilvus import Collection, CollectionSchema, DataType, FieldSchema, connections, utility
except Exception:  # noqa: BLE001
    Collection = None
    CollectionSchema = None
    DataType = None
    FieldSchema = None
    connections = None
    utility = None


class MilvusVectorRepository:
    def __init__(self, collection_name: str, dim: int) -> None:
        if connections is None:
            raise VectorStoreException("Milvus 依赖不可用")

        self.collection_name = collection_name
        self.dim = dim
        self.collection = None
        self._connect()

    def insert_documents(
        self,
        courseware_id: str,
        documents: List[Dict[str, Any]],
        vectors: List[List[float]],
    ) -> int:
        if not documents:
            return 0

        if self.collection is None:
            raise VectorStoreException("Milvus collection 未初始化")

        texts = [_normalize_content(document) for document in documents]
        entities = [
            [courseware_id] * len(documents),
            [str(document.get("node_id", "")) for document in documents],
            texts,
            vectors,
        ]

        try:
            result = self.collection.insert(entities)
            self.collection.flush()
            logger.info("Milvus insert completed. coursewareId=%s count=%s", courseware_id, len(documents))
            return len(result.primary_keys)
        except Exception as exc:  # noqa: BLE001
            logger.exception("Milvus insert failed. coursewareId=%s count=%s", courseware_id, len(documents))
            raise VectorStoreException("Milvus 写入失败") from exc

    def search_similar(
        self,
        *,
        query: str,
        query_vector: List[float] | None,
        courseware_id: str | None,
        top_k: int,
    ) -> List[Dict[str, Any]]:
        if self.collection is None:
            raise VectorStoreException("Milvus collection 未初始化")
        if query_vector is None:
            raise VectorStoreException("查询向量缺失")

        search_params = {"metric_type": "IP", "params": {"nprobe": 10}}
        expr = f"courseware_id == '{courseware_id}'" if courseware_id else None

        try:
            results = self.collection.search(
                data=[query_vector],
                anns_field="vector",
                param=search_params,
                limit=top_k,
                expr=expr,
                output_fields=["courseware_id", "node_id", "content"],
            )
        except Exception as exc:  # noqa: BLE001
            logger.exception("Milvus search failed. coursewareId=%s topK=%s", courseware_id, top_k)
            raise VectorStoreException("Milvus 检索失败") from exc

        output: List[Dict[str, Any]] = []
        for hits in results:
            for hit in hits:
                output.append(
                    {
                        "id": hit.id,
                        "distance": float(hit.distance),
                        "courseware_id": hit.entity.get("courseware_id"),
                        "node_id": hit.entity.get("node_id"),
                        "content": hit.entity.get("content"),
                    }
                )
        return output

    def _connect(self) -> None:
        logger.info("Initializing Milvus vector repository.")
        try:
            connections.connect(
                alias="default",
                uri=settings.MILVUS_URI,
                user=settings.MILVUS_USER,
                password=settings.MILVUS_PASSWORD,
                db_name=settings.MILVUS_DB_NAME,
            )
            self._init_collection()
            logger.info("Milvus vector repository is ready. collection=%s", self.collection_name)
        except Exception as exc:  # noqa: BLE001
            logger.warning("Milvus unavailable. Fallback will be used. reason=%s", type(exc).__name__)
            raise VectorStoreException("Milvus 不可用") from exc

    def _init_collection(self) -> None:
        if utility.has_collection(self.collection_name):
            self.collection = Collection(self.collection_name)
            self.collection.load()
            return

        fields = [
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="courseware_id", dtype=DataType.VARCHAR, max_length=128),
            FieldSchema(name="node_id", dtype=DataType.VARCHAR, max_length=128),
            FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535),
            FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=self.dim),
        ]
        schema = CollectionSchema(fields=fields, description="Courseware knowledge fragments")
        self.collection = Collection(name=self.collection_name, schema=schema)
        self.collection.create_index(
            field_name="vector",
            index_params={
                "metric_type": "IP",
                "index_type": "IVF_FLAT",
                "params": {"nlist": 1024},
            },
        )
        self.collection.load()


class KeywordVectorRepository:
    def __init__(self) -> None:
        self._documents: List[Dict[str, Any]] = []
        self._id_sequence = count(1)

    def insert_documents(
        self,
        courseware_id: str,
        documents: List[Dict[str, Any]],
        vectors: List[List[float]] | None = None,
    ) -> int:
        inserted = 0
        for document in documents:
            content = _normalize_content(document)
            if not content:
                continue
            self._documents.append(
                {
                    "id": next(self._id_sequence),
                    "courseware_id": courseware_id,
                    "node_id": str(document.get("node_id", "")),
                    "content": content,
                }
            )
            inserted += 1
        logger.info("Keyword fallback repository stored documents. coursewareId=%s count=%s", courseware_id, inserted)
        return inserted

    def search_similar(
        self,
        *,
        query: str,
        query_vector: List[float] | None,
        courseware_id: str | None,
        top_k: int,
    ) -> List[Dict[str, Any]]:
        filtered = [
            document
            for document in self._documents
            if courseware_id is None or document["courseware_id"] == courseware_id
        ]

        scored = []
        for document in filtered:
            score = _keyword_score(query, document["content"])
            if score <= 0:
                continue
            scored.append(
                {
                    "id": document["id"],
                    "distance": float(score),
                    "courseware_id": document["courseware_id"],
                    "node_id": document["node_id"],
                    "content": document["content"],
                }
            )

        scored.sort(key=lambda item: item["distance"], reverse=True)
        return scored[:top_k]


def _normalize_content(document: Dict[str, Any]) -> str:
    content = document.get("content") or document.get("text") or ""
    return str(content).strip()


def _keyword_score(query: str, content: str) -> float:
    query_lower = query.lower().strip()
    content_lower = content.lower()
    score = 2.0 if query_lower and query_lower in content_lower else 0.0
    for token in _tokenize(query_lower):
        if token and token in content_lower:
            score += 1.0
    return score


def _tokenize(text: str) -> List[str]:
    return [token for token in re.findall(r"[a-zA-Z0-9_]+|[\u4e00-\u9fff]+", text) if token]
