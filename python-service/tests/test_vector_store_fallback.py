from __future__ import annotations

from app.core.exceptions import VectorStoreException
from app.repositories.vector_repository import KeywordVectorRepository
from app.services import vector_store as vector_store_module


def test_vector_store_uses_fallback_when_milvus_unavailable(monkeypatch):
    monkeypatch.setattr(vector_store_module, "vector_store", None)

    def fail_build_repository(self):
        raise VectorStoreException("milvus down")

    monkeypatch.setattr(vector_store_module.VectorStoreManager, "_build_repository", lambda self: self.fallback_repository)

    store = vector_store_module.get_vector_store()

    assert isinstance(store.repository, KeywordVectorRepository)
    assert store.backend_name == "keyword_fallback"

    inserted = store.insert_documents(
        "cware_vector_1",
        [{"node_id": "node_1", "content": "递归需要明确终止条件"}],
    )
    results = store.search_similar("终止条件", courseware_id="cware_vector_1", top_k=1)

    assert inserted == 1
    assert len(results) == 1
    assert results[0]["node_id"] == "node_1"
