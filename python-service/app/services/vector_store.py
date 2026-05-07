from __future__ import annotations

from typing import Any, Dict, List

from app.clients.embedding_client import EmbeddingClient
from app.core.config import settings
from app.core.exceptions import VectorStoreException
from app.repositories.vector_repository import KeywordVectorRepository, MilvusVectorRepository
from app.utils.logger import logger


class VectorStoreManager:
    def __init__(self) -> None:
        self.collection_name = settings.COLLECTION_NAME
        self.dim = settings.EMBEDDING_DIM_SIZE
        self.embedding_client = EmbeddingClient()
        self.fallback_repository = KeywordVectorRepository()
        self.repository = self._build_repository()
        self.backend_name = "milvus" if isinstance(self.repository, MilvusVectorRepository) else "keyword_fallback"

    def embed_text(self, text: str) -> List[float]:
        return self.embedding_client.embed_text(text)

    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        return self.embedding_client.embed_batch(texts)

    def insert_documents(self, courseware_id: str, documents: List[Dict[str, Any]]) -> int:
        if not documents:
            return 0

        normalized_documents = [self._normalize_document(document) for document in documents]
        fallback_count = self.fallback_repository.insert_documents(courseware_id, normalized_documents, vectors=None)

        if isinstance(self.repository, KeywordVectorRepository):
            return fallback_count

        try:
            texts = [document["content"] for document in normalized_documents]
            vectors = self.embed_batch(texts)
            return self.repository.insert_documents(courseware_id, normalized_documents, vectors)
        except VectorStoreException:
            logger.warning("Primary vector backend failed during insert. Switching to fallback store.")
            self.repository = self.fallback_repository
            self.backend_name = "keyword_fallback"
            return fallback_count

    def search_similar(
        self,
        query: str,
        courseware_id: str | None = None,
        top_k: int = 3,
    ) -> List[Dict[str, Any]]:
        if isinstance(self.repository, KeywordVectorRepository):
            return self.fallback_repository.search_similar(
                query=query,
                query_vector=None,
                courseware_id=courseware_id,
                top_k=top_k,
            )

        try:
            query_vector = self.embed_text(query)
            results = self.repository.search_similar(
                query=query,
                query_vector=query_vector,
                courseware_id=courseware_id,
                top_k=top_k,
            )
            if results:
                return results
        except VectorStoreException:
            logger.warning("Primary vector backend failed during search. Using fallback search.")
            self.repository = self.fallback_repository
            self.backend_name = "keyword_fallback"

        return self.fallback_repository.search_similar(
            query=query,
            query_vector=None,
            courseware_id=courseware_id,
            top_k=top_k,
        )

    def _build_repository(self):
        try:
            repository = MilvusVectorRepository(self.collection_name, self.dim)
            logger.info("Using Milvus vector repository.")
            return repository
        except VectorStoreException:
            logger.warning("Using fallback keyword vector repository.")
            return self.fallback_repository

    def _normalize_document(self, document: Dict[str, Any]) -> Dict[str, Any]:
        content = (document.get("content") or document.get("text") or "").strip()
        return {
            "node_id": document.get("node_id") or document.get("nodeId") or "",
            "content": content,
        }


vector_store: VectorStoreManager | None = None


def get_vector_store() -> VectorStoreManager:
    global vector_store
    if vector_store is None:
        vector_store = VectorStoreManager()
    return vector_store
