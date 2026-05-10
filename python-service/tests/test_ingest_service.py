from __future__ import annotations

from app.services import ingest_service as ingest_service_module


def test_ingest_courseware_chunks_returns_zero_for_empty_chunks(monkeypatch) -> None:
    class FakeStore:
        backend_name = "keyword_fallback"

        def insert_documents(self, courseware_id, documents):
            raise AssertionError("insert_documents should not be called for empty chunks")

    monkeypatch.setattr(ingest_service_module, "get_vector_store", lambda: FakeStore())

    result = ingest_service_module.ingest_courseware_chunks("cware_empty", [])

    assert result == {
        "courseware_id": "cware_empty",
        "inserted": 0,
        "backend": "keyword_fallback",
    }


def test_ingest_courseware_chunks_inserts_documents_and_reports_backend(monkeypatch) -> None:
    captured: dict[str, object] = {}

    class FakeStore:
        backend_name = "milvus"

        def insert_documents(self, courseware_id, documents):
            captured["courseware_id"] = courseware_id
            captured["documents"] = documents
            return len(documents)

    monkeypatch.setattr(ingest_service_module, "get_vector_store", lambda: FakeStore())

    chunks = [
        {
            "chunk_id": "cware_demo_p001_c000",
            "courseware_id": "cware_demo",
            "page_index": 1,
            "chunk_index": 0,
            "text": "第一页内容",
            "content": "第一页内容",
            "sentences": ["第一页内容"],
            "metadata": {"courseware_id": "cware_demo", "page_index": 1, "chunk_index": 0},
        },
        {
            "chunk_id": "cware_demo_p002_c000",
            "courseware_id": "cware_demo",
            "page_index": 2,
            "chunk_index": 0,
            "text": "第二页内容",
            "content": "第二页内容",
            "sentences": ["第二页内容"],
            "metadata": {"courseware_id": "cware_demo", "page_index": 2, "chunk_index": 0},
        },
    ]

    result = ingest_service_module.ingest_courseware_chunks("cware_demo", chunks)

    assert captured["courseware_id"] == "cware_demo"
    assert captured["documents"] == chunks
    assert result == {
        "courseware_id": "cware_demo",
        "inserted": 2,
        "backend": "milvus",
    }
