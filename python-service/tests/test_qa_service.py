from __future__ import annotations

from app.api.v1 import qa as qa_api
from app.core.config import settings
from app.schemas.qa import QaAskTextRequest, QaAskTextResponse
from app.services import rag_service


def _build_request(**overrides) -> QaAskTextRequest:
    payload = {
        "session_id": "sess_qa_1",
        "courseware_id": "cware_qa_1",
        "page_index": 3,
        "question": "这一页在讲什么",
        "top_k": 5,
    }
    payload.update(overrides)
    return QaAskTextRequest(**payload)


def test_answer_question_uses_llm_when_evidence_available(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "test-key")
    monkeypatch.setattr(
        rag_service,
        "retrieve_context",
        lambda **kwargs: [
            {
                "chunk_id": "cware_qa_1_p003_c000",
                "page_index": 3,
                "text": "这一页重点介绍递归的终止条件。",
                "source": "page_3",
                "score": 0.82,
                "adjusted_score": 1.12,
                "metadata": {"page_index": 3},
            }
        ],
    )

    class _FakeLLMClient:
        def invoke(self, messages):
            assert "只能根据给定的课件 evidence 回答问题" in messages[0].content
            assert "课堂提问：这一页在讲什么" in messages[1].content
            return "这一页主要在讲递归什么时候该停下来。"

    monkeypatch.setattr(rag_service, "get_llm_client", lambda: _FakeLLMClient())

    result = rag_service.answer_question(_build_request())

    assert isinstance(result, QaAskTextResponse)
    assert result.answer == "这一页主要在讲递归什么时候该停下来。"
    assert result.evidence[0].page_index == 3
    assert result.evidence[0].chunk_id == "cware_qa_1_p003_c000"
    assert result.latency_ms >= 1


def test_answer_question_returns_friendly_message_when_no_evidence(monkeypatch):
    monkeypatch.setattr(rag_service, "retrieve_context", lambda **kwargs: [])

    result = rag_service.answer_question(_build_request())

    assert "课件中没有直接覆盖该内容" in result.answer
    assert result.evidence == []
    assert result.latency_ms >= 1


def test_answer_question_falls_back_to_template_when_api_key_missing(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "")
    monkeypatch.setattr(
        rag_service,
        "retrieve_context",
        lambda **kwargs: [
            {
                "chunk_id": "cware_qa_1_p002_c000",
                "page_index": 2,
                "text": "这一页主要解释链表由节点和指针组成。",
                "source": "page_2",
                "score": 0.71,
                "adjusted_score": 1.01,
                "metadata": {"page_index": 2},
            }
        ],
    )

    result = rag_service.answer_question(_build_request(question="链表是什么"))

    assert "根据课件第2页" in result.answer
    assert "节点和指针" in result.answer
    assert result.evidence[0].page_index == 2


def test_answer_question_falls_back_to_template_when_llm_fails(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "test-key")
    monkeypatch.setattr(
        rag_service,
        "retrieve_context",
        lambda **kwargs: [
            {
                "chunk_id": "cware_qa_1_p001_c001",
                "page_index": 1,
                "text": "这一页强调循环要先明确初始化、条件和迭代更新。",
                "source": "page_1",
                "score": 0.68,
                "adjusted_score": 0.98,
                "metadata": {"page_index": 1},
            }
        ],
    )

    class _BrokenLLMClient:
        def invoke(self, _messages):
            raise RuntimeError("llm down")

    monkeypatch.setattr(rag_service, "get_llm_client", lambda: _BrokenLLMClient())

    result = rag_service.answer_question(_build_request(question="循环要注意什么"))

    assert "根据课件第1页" in result.answer
    assert result.evidence[0].chunk_id == "cware_qa_1_p001_c001"


def test_qa_request_accepts_camel_case_fields(request_app, monkeypatch):
    captured = {}

    def fake_answer_question(request: QaAskTextRequest) -> QaAskTextResponse:
        captured["session_id"] = request.session_id
        captured["courseware_id"] = request.courseware_id
        captured["page_index"] = request.page_index
        captured["top_k"] = request.top_k
        return QaAskTextResponse(
            answer="根据课件第3页，这是一个关于递归终止条件的问题。",
            evidence=[],
            latency_ms=12,
        )

    monkeypatch.setattr(qa_api, "answer_question", fake_answer_question)

    response = request_app(
        "POST",
        "/python/v1/qa/ask-text",
        json={
            "sessionId": "sess_api_1",
            "coursewareId": "cware_api_1",
            "pageIndex": 3,
            "question": "这一页讲什么",
            "topK": 4,
        },
    )

    payload = response.json()

    assert response.status_code == 200
    assert payload["code"] == 0
    assert payload["message"] == "success"
    assert payload["data"]["answer"]
    assert payload["data"]["latencyMs"] == 12
    assert captured == {
        "session_id": "sess_api_1",
        "courseware_id": "cware_api_1",
        "page_index": 3,
        "top_k": 4,
    }
