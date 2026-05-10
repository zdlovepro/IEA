from __future__ import annotations

from app.repositories.vector_repository import KeywordVectorRepository
from app.services import vector_store as vector_store_module


def test_keyword_vector_repository_preserves_chunk_metadata() -> None:
    repository = KeywordVectorRepository()

    inserted = repository.insert_documents(
        "cware_repo",
        [
            {
                "node_id": "node_001",
                "chunk_id": "cware_repo_p001_c000",
                "page_index": 1,
                "content": "递归需要明确终止条件和递归关系。",
                "metadata": {
                    "courseware_id": "cware_repo",
                    "page_index": 1,
                    "chunk_index": 0,
                },
            }
        ],
        vectors=None,
    )
    results = repository.search_similar(
        query="终止条件",
        query_vector=None,
        courseware_id="cware_repo",
        top_k=1,
    )

    assert inserted == 1
    assert len(results) == 1
    assert results[0]["chunk_id"] == "cware_repo_p001_c000"
    assert results[0]["page_index"] == 1
    assert results[0]["content"] == "递归需要明确终止条件和递归关系。"
    assert results[0]["metadata"]["page_index"] == 1
    assert "distance" in results[0]
    assert "score" in results[0]


def test_vector_store_falls_back_when_milvus_schema_is_incompatible(monkeypatch) -> None:
    monkeypatch.setattr(vector_store_module, "vector_store", None)

    monkeypatch.setattr(
        vector_store_module.VectorStoreManager,
        "_build_repository",
        lambda self: self.fallback_repository,
    )

    store = vector_store_module.get_vector_store()
    inserted = store.insert_documents(
        "cware_schema",
        [
            {
                "chunk_id": "cware_schema_p001_c000",
                "page_index": 1,
                "content": "动态规划需要定义状态和转移方程。",
                "metadata": {
                    "courseware_id": "cware_schema",
                    "page_index": 1,
                    "chunk_index": 0,
                },
            }
        ],
    )
    results = store.search_similar("转移方程", courseware_id="cware_schema", top_k=1)

    assert store.backend_name == "keyword_fallback"
    assert inserted == 1
    assert len(results) == 1
    assert results[0]["chunk_id"] == "cware_schema_p001_c000"
    assert results[0]["page_index"] == 1
    assert results[0]["metadata"]["page_index"] == 1
