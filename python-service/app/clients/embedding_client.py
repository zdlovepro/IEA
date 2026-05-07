from __future__ import annotations

import os
from typing import List

from langchain_community.embeddings import HuggingFaceBgeEmbeddings

from app.core.config import settings
from app.core.exceptions import VectorStoreException
from app.utils.logger import logger


class EmbeddingClient:
    def __init__(self) -> None:
        self.model_name = settings.EMBEDDING_MODEL_NAME
        self._embeddings: HuggingFaceBgeEmbeddings | None = None

    def embed_text(self, text: str) -> List[float]:
        try:
            return self._get_embeddings().embed_query(text)
        except VectorStoreException:
            raise
        except Exception as exc:  # noqa: BLE001
            logger.exception("Embedding single text failed. model=%s", self.model_name)
            raise VectorStoreException("文本向量化失败") from exc

    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        try:
            return self._get_embeddings().embed_documents(texts)
        except VectorStoreException:
            raise
        except Exception as exc:  # noqa: BLE001
            logger.exception("Embedding batch texts failed. model=%s count=%s", self.model_name, len(texts))
            raise VectorStoreException("批量文本向量化失败") from exc

    def _get_embeddings(self) -> HuggingFaceBgeEmbeddings:
        if self._embeddings is None:
            try:
                os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
                self._embeddings = HuggingFaceBgeEmbeddings(
                    model_name=self.model_name,
                    model_kwargs={"device": "cpu"},
                    encode_kwargs={"normalize_embeddings": True},
                )
                logger.info("Embedding model initialized. model=%s", self.model_name)
            except Exception as exc:  # noqa: BLE001
                logger.exception("Embedding model initialization failed. model=%s", self.model_name)
                raise VectorStoreException("向量模型初始化失败") from exc
        return self._embeddings
