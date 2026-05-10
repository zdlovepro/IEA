from __future__ import annotations

import json
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
    _REQUIRED_FIELDS = {
        "courseware_id",
        "node_id",
        "chunk_id",
        "page_index",
        "content",
        "metadata_json",
        "vector",
    }

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
        if len(documents) != len(vectors):
            raise VectorStoreException("向量数量与文档数量不一致")

        entities = [
            [courseware_id] * len(documents),
            [str(document.get("node_id", "")) for document in documents],
            [str(document.get("chunk_id", "")) for document in documents],
            [int(document.get("page_index", 0)) for document in documents],
            [_normalize_content(document) for document in documents],
            [_metadata_json(document) for document in documents],
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
                output_fields=[
                    "courseware_id",
                    "node_id",
                    "chunk_id",
                    "page_index",
                    "content",
                    "metadata_json",
                ],
            )
        except Exception as exc:  # noqa: BLE001
            logger.exception("Milvus search failed. coursewareId=%s topK=%s", courseware_id, top_k)
            raise VectorStoreException("Milvus 检索失败") from exc

        output: List[Dict[str, Any]] = []
        for hits in results:
            for hit in hits:
                metadata = _parse_metadata_json(hit.entity.get("metadata_json"))
                output.append(
                    {
                        "id": hit.id,
                        "score": float(hit.distance),
                        "distance": float(hit.distance),
                        "courseware_id": hit.entity.get("courseware_id"),
                        "node_id": hit.entity.get("node_id"),
                        "chunk_id": hit.entity.get("chunk_id"),
                        "page_index": int(hit.entity.get("page_index") or 0),
                        "content": hit.entity.get("content"),
                        "metadata": metadata,
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
        except VectorStoreException:
            raise
        except Exception as exc:  # noqa: BLE001
            logger.warning("Milvus unavailable. Fallback will be used. reason=%s", type(exc).__name__)
            raise VectorStoreException("Milvus 不可用") from exc

    def _init_collection(self) -> None:
        if utility.has_collection(self.collection_name):
            self.collection = Collection(self.collection_name)
            if not self._is_collection_schema_compatible():
                logger.warning(
                    "Milvus collection schema incompatible. collection=%s requiredFields=%s",
                    self.collection_name,
                    sorted(self._REQUIRED_FIELDS),
                )
                raise VectorStoreException("Milvus collection schema incompatible")
            self.collection.load()
            return

        fields = [
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="courseware_id", dtype=DataType.VARCHAR, max_length=128),
            FieldSchema(name="node_id", dtype=DataType.VARCHAR, max_length=128),
            FieldSchema(name="chunk_id", dtype=DataType.VARCHAR, max_length=128),
            FieldSchema(name="page_index", dtype=DataType.INT64),
            FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535),
            FieldSchema(name="metadata_json", dtype=DataType.VARCHAR, max_length=65535),
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

    def _is_collection_schema_compatible(self) -> bool:
        if self.collection is None or getattr(self.collection, "schema", None) is None:
            return False

        fields = {field.name: field for field in self.collection.schema.fields}
        if not self._REQUIRED_FIELDS.issubset(fields.keys()):
            return False

        vector_field = fields.get("vector")
        vector_dim = getattr(vector_field, "params", {}).get("dim") if vector_field is not None else None
        return vector_dim == self.dim


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
                    "chunk_id": str(document.get("chunk_id", "")),
                    "page_index": int(document.get("page_index", 0)),
                    "content": content,
                    "metadata": _metadata_dict(document),
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
                    "score": float(score),
                    "distance": float(score),
                    "courseware_id": document["courseware_id"],
                    "node_id": document["node_id"],
                    "chunk_id": document["chunk_id"],
                    "page_index": document["page_index"],
                    "content": document["content"],
                    "metadata": document["metadata"],
                }
            )

        scored.sort(key=lambda item: item["score"], reverse=True)
        return scored[:top_k]


def _normalize_content(document: Dict[str, Any]) -> str:
    content = document.get("content") or document.get("text") or ""
    return str(content).strip()


def _metadata_dict(document: Dict[str, Any]) -> Dict[str, Any]:
    metadata = document.get("metadata")
    if isinstance(metadata, dict):
        return metadata
    return {}


def _metadata_json(document: Dict[str, Any]) -> str:
    return json.dumps(_metadata_dict(document), ensure_ascii=False, sort_keys=True)


def _parse_metadata_json(raw_value: Any) -> Dict[str, Any]:
    if not raw_value:
        return {}
    if isinstance(raw_value, dict):
        return raw_value
    try:
        parsed = json.loads(str(raw_value))
    except Exception:  # noqa: BLE001
        return {}
    return parsed if isinstance(parsed, dict) else {}


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
