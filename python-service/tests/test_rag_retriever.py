from __future__ import annotations

from app.repositories.vector_repository import KeywordVectorRepository
from app.services import rag_retriever as rag_retriever_module


def test_retrieve_context_prefers_current_page(monkeypatch) -> None:
    captured: dict[str, object] = {}

    class FakeStore:
        def search_similar(self, *, query, courseware_id, top_k):
            captured["top_k"] = top_k
            return [
                {"chunk_id": "far", "page_index": 4, "content": "远页内容", "score": 0.82, "metadata": {}},
                {"chunk_id": "current", "page_index": 2, "content": "当前页内容", "score": 0.60, "metadata": {}},
                {"chunk_id": "adjacent", "page_index": 1, "content": "相邻页内容", "score": 0.70, "metadata": {}},
            ]

    monkeypatch.setattr(rag_retriever_module, "get_vector_store", lambda: FakeStore())

    results = rag_retriever_module.retrieve_context(
        courseware_id="cware_current",
        question="当前页重点是什么",
        page_index=2,
        top_k=2,
    )

    assert captured["top_k"] == 6
    assert len(results) == 2
    assert results[0]["chunk_id"] == "current"
    assert results[0]["page_index"] == 2
    assert results[0]["source"] == "page_2"
    assert results[0]["adjusted_score"] > results[0]["score"]


def test_retrieve_context_prefers_adjacent_page_over_distant_page(monkeypatch) -> None:
    class FakeStore:
        def search_similar(self, *, query, courseware_id, top_k):
            return [
                {"chunk_id": "distant", "page_index": 7, "content": "远页内容", "score": 0.75, "metadata": {}},
                {"chunk_id": "adjacent", "page_index": 4, "content": "相邻页内容", "score": 0.70, "metadata": {}},
            ]

    monkeypatch.setattr(rag_retriever_module, "get_vector_store", lambda: FakeStore())

    results = rag_retriever_module.retrieve_context(
        courseware_id="cware_adjacent",
        question="上一页讲了什么",
        page_index=5,
        top_k=2,
    )

    assert [item["chunk_id"] for item in results] == ["adjacent", "distant"]
    assert results[0]["adjusted_score"] > results[1]["adjusted_score"]


def test_retrieve_context_uses_original_score_when_page_index_not_provided(monkeypatch) -> None:
    class FakeStore:
        def search_similar(self, *, query, courseware_id, top_k):
            return [
                {"chunk_id": "higher", "page_index": 9, "content": "高分内容", "score": 0.91, "metadata": {}},
                {"chunk_id": "lower", "page_index": 1, "content": "低分内容", "score": 0.72, "metadata": {}},
            ]

    monkeypatch.setattr(rag_retriever_module, "get_vector_store", lambda: FakeStore())

    results = rag_retriever_module.retrieve_context(
        courseware_id="cware_no_page",
        question="课程重点",
        page_index=None,
        top_k=2,
    )

    assert [item["chunk_id"] for item in results] == ["higher", "lower"]
    assert results[0]["adjusted_score"] == results[0]["score"]
    assert results[1]["adjusted_score"] == results[1]["score"]


def test_retrieve_context_handles_missing_page_index_without_crashing(monkeypatch) -> None:
    class FakeStore:
        def search_similar(self, *, query, courseware_id, top_k):
            return [
                {"chunk_id": "missing-page", "content": "无页码内容", "score": 0.88, "metadata": {}},
                {"chunk_id": "current-page", "page_index": 3, "content": "当前页内容", "score": 0.62, "metadata": {}},
            ]

    monkeypatch.setattr(rag_retriever_module, "get_vector_store", lambda: FakeStore())

    results = rag_retriever_module.retrieve_context(
        courseware_id="cware_missing_page",
        question="这页在讲什么",
        page_index=3,
        top_k=2,
    )

    assert len(results) == 2
    missing_page_result = next(item for item in results if item["chunk_id"] == "missing-page")
    assert missing_page_result["page_index"] is None
    assert missing_page_result["source"] == "courseware"
    assert missing_page_result["adjusted_score"] == missing_page_result["score"]


def test_retrieve_context_returns_empty_list_when_no_candidates(monkeypatch) -> None:
    class FakeStore:
        def search_similar(self, *, query, courseware_id, top_k):
            return []

    monkeypatch.setattr(rag_retriever_module, "get_vector_store", lambda: FakeStore())

    results = rag_retriever_module.retrieve_context(
        courseware_id="cware_empty",
        question="没有结果吗",
        page_index=1,
        top_k=3,
    )

    assert results == []


def test_retrieve_context_works_with_keyword_fallback_repository(monkeypatch) -> None:
    repository = KeywordVectorRepository()
    repository.insert_documents(
        "cware_fallback",
        [
            {
                "chunk_id": "cware_fallback_p002_c000",
                "page_index": 2,
                "content": "当前页重点介绍递归的终止条件和返回值。",
                "metadata": {"page_index": 2, "courseware_id": "cware_fallback"},
            },
            {
                "chunk_id": "cware_fallback_p004_c000",
                "page_index": 4,
                "content": "远页讲述的是循环结构和数组遍历。",
                "metadata": {"page_index": 4, "courseware_id": "cware_fallback"},
            },
        ],
        vectors=None,
    )

    class FallbackStore:
        def search_similar(self, *, query, courseware_id, top_k):
            return repository.search_similar(
                query=query,
                query_vector=None,
                courseware_id=courseware_id,
                top_k=top_k,
            )

    monkeypatch.setattr(rag_retriever_module, "get_vector_store", lambda: FallbackStore())

    results = rag_retriever_module.retrieve_context(
        courseware_id="cware_fallback",
        question="终止条件",
        page_index=2,
        top_k=2,
    )

    assert len(results) == 1
    assert results[0]["chunk_id"] == "cware_fallback_p002_c000"
    assert results[0]["page_index"] == 2
    assert results[0]["source"] == "page_2"
    assert results[0]["text"] == "当前页重点介绍递归的终止条件和返回值。"


def test_retrieve_context_returns_empty_list_when_search_fails(monkeypatch) -> None:
    class FakeStore:
        def search_similar(self, *, query, courseware_id, top_k):
            raise RuntimeError("search down")

    monkeypatch.setattr(rag_retriever_module, "get_vector_store", lambda: FakeStore())

    results = rag_retriever_module.retrieve_context(
        courseware_id="cware_error",
        question="异常情况",
        page_index=1,
        top_k=2,
    )

    assert results == []
