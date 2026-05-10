from __future__ import annotations

import os
from typing import List

from langchain_community.embeddings import HuggingFaceBgeEmbeddings
from openai import OpenAI

from app.core.config import settings
from app.core.exceptions import VectorStoreException
from app.utils.logger import logger


class EmbeddingClient:
    _LOCAL_DEFAULT_MODEL = "BAAI/bge-large-zh-v1.5"
    _DASHSCOPE_BATCH_SIZE = 10

    def __init__(self) -> None:
        self.provider = (settings.EMBEDDING_PROVIDER or "dashscope").strip().lower()
        self.model_name = self._resolve_model_name()
        self.dim_size = settings.EMBEDDING_DIM_SIZE
        self._embeddings: HuggingFaceBgeEmbeddings | None = None
        self._client: OpenAI | None = None

    def embed_text(self, text: str) -> List[float]:
        return self.embed_batch([text])[0]

    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        normalized_texts = [self._normalize_text(text) for text in texts]
        if not normalized_texts:
            return []

        if self.provider == "local":
            return self._embed_batch_local(normalized_texts)
        if self.provider == "dashscope":
            return self._embed_batch_dashscope(normalized_texts)

        raise VectorStoreException(f"Unsupported embedding provider: {self.provider}")

    def _embed_batch_local(self, texts: List[str]) -> List[List[float]]:
        try:
            embeddings_model = self._get_embeddings()
            results: List[List[float]] = []
            for text in texts:
                if not text:
                    results.append(self._zero_vector())
                    continue
                embedding = embeddings_model.embed_query(text)
                results.append(self._validate_embedding(embedding))
            return results
        except VectorStoreException:
            raise
        except Exception as exc:  # noqa: BLE001
            logger.exception("Embedding batch texts failed. provider=%s model=%s count=%s", self.provider, self.model_name, len(texts))
            raise VectorStoreException("文本向量化失败") from exc

    def _embed_batch_dashscope(self, texts: List[str]) -> List[List[float]]:
        api_key = self._resolve_api_key()
        if not api_key:
            raise VectorStoreException("Embedding API 未配置")

        results: List[List[float] | None] = [None] * len(texts)
        non_empty_items = [(index, text) for index, text in enumerate(texts) if text]

        for batch_start in range(0, len(non_empty_items), self._DASHSCOPE_BATCH_SIZE):
            batch = non_empty_items[batch_start:batch_start + self._DASHSCOPE_BATCH_SIZE]
            batch_indices = [item[0] for item in batch]
            batch_texts = [item[1] for item in batch]

            try:
                response = self._get_client(api_key).embeddings.create(
                    model=self.model_name,
                    input=batch_texts,
                    dimensions=self.dim_size,
                )
            except Exception as exc:  # noqa: BLE001
                logger.exception(
                    "Embedding API request failed. provider=%s model=%s batchSize=%s",
                    self.provider,
                    self.model_name,
                    len(batch_texts),
                )
                raise VectorStoreException("Embedding API 调用失败") from exc

            response_data = getattr(response, "data", None) or []
            if len(response_data) != len(batch_texts):
                raise VectorStoreException("Embedding API 调用失败")

            for item_index, item in enumerate(response_data):
                embedding = getattr(item, "embedding", None)
                results[batch_indices[item_index]] = self._validate_embedding(embedding)

        finalized_results: List[List[float]] = []
        for embedding in results:
            finalized_results.append(embedding if embedding is not None else self._zero_vector())
        return finalized_results

    def _get_embeddings(self) -> HuggingFaceBgeEmbeddings:
        if self._embeddings is None:
            try:
                os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
                self._embeddings = HuggingFaceBgeEmbeddings(
                    model_name=self.model_name,
                    model_kwargs={"device": "cpu"},
                    encode_kwargs={"normalize_embeddings": True},
                )
                logger.info("Embedding model initialized. provider=%s model=%s", self.provider, self.model_name)
            except Exception as exc:  # noqa: BLE001
                logger.exception("Embedding model initialization failed. provider=%s model=%s", self.provider, self.model_name)
                raise VectorStoreException("向量模型初始化失败") from exc
        return self._embeddings

    def _get_client(self, api_key: str) -> OpenAI:
        if self._client is None:
            self._client = OpenAI(
                api_key=api_key,
                base_url=settings.EMBEDDING_API_BASE,
            )
            logger.info("Embedding API client initialized. provider=%s model=%s", self.provider, self.model_name)
        return self._client

    def _resolve_model_name(self) -> str:
        configured_model = (settings.EMBEDDING_MODEL_NAME or "").strip()
        if self.provider == "local":
            return configured_model if configured_model and configured_model != "text-embedding-v4" else self._LOCAL_DEFAULT_MODEL
        return configured_model or "text-embedding-v4"

    @staticmethod
    def _normalize_text(text: str | None) -> str:
        return str(text or "").strip()

    def _resolve_api_key(self) -> str | None:
        embedding_api_key = (settings.EMBEDDING_API_KEY or "").strip()
        dashscope_api_key = (settings.DASHSCOPE_API_KEY or "").strip()
        return embedding_api_key or dashscope_api_key or None

    def _validate_embedding(self, embedding: List[float] | None) -> List[float]:
        if not isinstance(embedding, list) or len(embedding) != self.dim_size:
            raise VectorStoreException("Embedding 向量维度不匹配")
        return [float(value) for value in embedding]

    def _zero_vector(self) -> List[float]:
        return [0.0] * self.dim_size
