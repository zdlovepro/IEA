from __future__ import annotations

from types import SimpleNamespace

import pytest

from app.clients import embedding_client as embedding_client_module
from app.core.exceptions import VectorStoreException


def test_dashscope_embed_batch_success_returns_1024_dim_vectors(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_PROVIDER", "dashscope", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_API_KEY", "embedding-key", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "DASHSCOPE_API_KEY", "", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_MODEL_NAME", "text-embedding-v4", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_DIM_SIZE", 1024, raising=False)

    class FakeEmbeddingsApi:
        def create(self, *, model, input, dimensions):
            assert model == "text-embedding-v4"
            assert dimensions == 1024
            return SimpleNamespace(
                data=[SimpleNamespace(embedding=[float(index)] * 1024) for index, _ in enumerate(input, start=1)]
            )

    class FakeOpenAI:
        def __init__(self, *, api_key, base_url):
            assert api_key == "embedding-key"
            assert base_url == embedding_client_module.settings.EMBEDDING_API_BASE
            self.embeddings = FakeEmbeddingsApi()

    monkeypatch.setattr(embedding_client_module, "OpenAI", FakeOpenAI)

    client = embedding_client_module.EmbeddingClient()
    vectors = client.embed_batch(["第一页内容", "第二页内容"])

    assert len(vectors) == 2
    assert len(vectors[0]) == 1024
    assert vectors[0][0] == 1.0
    assert vectors[1][0] == 2.0


def test_dashscope_embed_batch_uses_fallback_dashscope_api_key(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_PROVIDER", "dashscope", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_API_KEY", "", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "DASHSCOPE_API_KEY", "dashscope-key", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_DIM_SIZE", 4, raising=False)

    class FakeEmbeddingsApi:
        def create(self, *, model, input, dimensions):
            return SimpleNamespace(data=[SimpleNamespace(embedding=[0.5] * dimensions) for _ in input])

    class FakeOpenAI:
        def __init__(self, *, api_key, base_url):
            assert api_key == "dashscope-key"
            self.embeddings = FakeEmbeddingsApi()

    monkeypatch.setattr(embedding_client_module, "OpenAI", FakeOpenAI)

    client = embedding_client_module.EmbeddingClient()
    vector = client.embed_text("测试文本")

    assert vector == [0.5, 0.5, 0.5, 0.5]


def test_dashscope_embed_batch_raises_when_api_key_missing(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_PROVIDER", "dashscope", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_API_KEY", "", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "DASHSCOPE_API_KEY", "", raising=False)

    client = embedding_client_module.EmbeddingClient()

    with pytest.raises(VectorStoreException, match="Embedding API 未配置"):
        client.embed_text("测试文本")


def test_dashscope_embed_batch_raises_when_api_call_fails(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_PROVIDER", "dashscope", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_API_KEY", "embedding-key", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "DASHSCOPE_API_KEY", "", raising=False)

    class FakeEmbeddingsApi:
        def create(self, *, model, input, dimensions):
            raise RuntimeError("boom")

    class FakeOpenAI:
        def __init__(self, *, api_key, base_url):
            self.embeddings = FakeEmbeddingsApi()

    monkeypatch.setattr(embedding_client_module, "OpenAI", FakeOpenAI)

    client = embedding_client_module.EmbeddingClient()

    with pytest.raises(VectorStoreException, match="Embedding API 调用失败"):
        client.embed_batch(["第一页内容"])


def test_dashscope_embed_batch_raises_when_dimension_is_not_1024(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_PROVIDER", "dashscope", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_API_KEY", "embedding-key", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "DASHSCOPE_API_KEY", "", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_DIM_SIZE", 1024, raising=False)

    class FakeEmbeddingsApi:
        def create(self, *, model, input, dimensions):
            return SimpleNamespace(data=[SimpleNamespace(embedding=[1.0] * 1000) for _ in input])

    class FakeOpenAI:
        def __init__(self, *, api_key, base_url):
            self.embeddings = FakeEmbeddingsApi()

    monkeypatch.setattr(embedding_client_module, "OpenAI", FakeOpenAI)

    client = embedding_client_module.EmbeddingClient()

    with pytest.raises(VectorStoreException, match="Embedding 向量维度不匹配"):
        client.embed_batch(["第一页内容"])


def test_dashscope_embed_batch_splits_requests_by_ten(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_PROVIDER", "dashscope", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_API_KEY", "embedding-key", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "DASHSCOPE_API_KEY", "", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_DIM_SIZE", 3, raising=False)

    batches: list[list[str]] = []

    class FakeEmbeddingsApi:
        def create(self, *, model, input, dimensions):
            batches.append(list(input))
            return SimpleNamespace(
                data=[
                    SimpleNamespace(embedding=[float(int(item.split("-")[1]))] * dimensions)
                    for item in input
                ]
            )

    class FakeOpenAI:
        def __init__(self, *, api_key, base_url):
            self.embeddings = FakeEmbeddingsApi()

    monkeypatch.setattr(embedding_client_module, "OpenAI", FakeOpenAI)

    texts = [f"text-{index}" for index in range(25)]
    client = embedding_client_module.EmbeddingClient()
    vectors = client.embed_batch(texts)

    assert [len(batch) for batch in batches] == [10, 10, 5]
    assert len(vectors) == 25
    assert vectors[0] == [0.0, 0.0, 0.0]
    assert vectors[24] == [24.0, 24.0, 24.0]


def test_local_provider_keeps_existing_bge_path(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_PROVIDER", "local", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_MODEL_NAME", "BAAI/bge-large-zh-v1.5", raising=False)
    monkeypatch.setattr(embedding_client_module.settings, "EMBEDDING_DIM_SIZE", 3, raising=False)

    class FakeEmbeddings:
        def embed_query(self, text: str) -> list[float]:
            return [float(len(text)), 1.0, 0.0]

    monkeypatch.setattr(embedding_client_module.EmbeddingClient, "_get_embeddings", lambda self: FakeEmbeddings())

    client = embedding_client_module.EmbeddingClient()
    vectors = client.embed_batch(["课件", "讲稿"])

    assert vectors == [[2.0, 1.0, 0.0], [2.0, 1.0, 0.0]]
