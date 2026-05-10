from __future__ import annotations

import asyncio

import httpx

from app.api.v1 import qa as qa_api
from app.core.config import settings
from app.main import app
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


def _sample_evidence(page_index: int = 3, text: str = "这一页重点介绍递归的终止条件。") -> list[dict]:
    return [
        {
            "chunk_id": f"cware_qa_1_p{page_index:03d}_c000",
            "page_index": page_index,
            "text": text,
            "source": f"page_{page_index}",
            "score": 0.82,
            "adjusted_score": 1.12,
            "metadata": {"page_index": page_index},
        }
    ]


def _collect_sse_lines(path: str) -> list[str]:
    async def _run() -> list[str]:
        transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            async with client.stream("GET", path) as response:
                assert response.status_code == 200
                return [line async for line in response.aiter_lines() if line]

    return asyncio.run(_run())


def test_answer_question_uses_llm_when_evidence_available(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "test-key")
    monkeypatch.setattr(rag_service, "retrieve_context", lambda **kwargs: _sample_evidence())

    class _FakeLLMClient:
        def invoke(self, messages):
            assert "只能根据课件 evidence 回答问题" in messages[0].content
            assert "课堂提问：这一页在讲什么" in messages[1].content
            return "这一页主要在讲递归应该在什么时候停下来。"

    monkeypatch.setattr(rag_service, "get_llm_client", lambda: _FakeLLMClient())

    result = rag_service.answer_question(_build_request())

    assert isinstance(result, QaAskTextResponse)
    assert result.answer == "这一页主要在讲递归应该在什么时候停下来。"
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
        lambda **kwargs: _sample_evidence(page_index=2, text="这一页主要解释链表由节点和指针组成。"),
    )

    result = rag_service.answer_question(_build_request(question="链表是什么"))

    assert "根据课件第 2 页" in result.answer
    assert "链表由节点和指针组成" in result.answer
    assert result.evidence[0].page_index == 2


def test_answer_question_falls_back_to_template_when_llm_fails(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "test-key")
    monkeypatch.setattr(
        rag_service,
        "retrieve_context",
        lambda **kwargs: _sample_evidence(page_index=1, text="这一页强调循环要先明确初始化、条件和迭代更新。"),
    )

    class _BrokenLLMClient:
        def invoke(self, _messages):
            raise RuntimeError("llm down")

    monkeypatch.setattr(rag_service, "get_llm_client", lambda: _BrokenLLMClient())

    result = rag_service.answer_question(_build_request(question="循环要注意什么"))

    assert "根据课件第 1 页" in result.answer
    assert result.evidence[0].chunk_id == "cware_qa_1_p001_c000"


def test_stream_answer_events_uses_llm_stream_when_available(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "test-key")
    monkeypatch.setattr(rag_service, "retrieve_context", lambda **kwargs: _sample_evidence())

    class _FakeLLMClient:
        def stream(self, messages):
            assert "课堂提问：这一页在讲什么" in messages[1].content
            yield "这一页"
            yield "适合流式"
            yield "回答。"

    monkeypatch.setattr(rag_service, "get_llm_client", lambda: _FakeLLMClient())

    events = list(rag_service.stream_answer_events(_build_request()))

    assert [event["type"] for event in events] == ["delta", "delta", "delta"]
    assert "".join(event["content"] for event in events) == "这一页适合流式回答。"


def test_stream_answer_events_fall_back_to_simulated_chunks_without_api_key(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "")
    monkeypatch.setattr(
        rag_service,
        "retrieve_context",
        lambda **kwargs: _sample_evidence(page_index=4, text="这一页说明二叉树遍历需要区分前序、中序和后序。"),
    )

    events = list(rag_service.stream_answer_events(_build_request(page_index=4, question="这页重点是什么")))

    assert events
    assert all(event["type"] == "delta" for event in events)
    assert "根据课件第 4 页" in "".join(event["content"] for event in events)


def test_qa_request_accepts_camel_case_fields(request_app, monkeypatch):
    captured = {}

    def fake_answer_question(request: QaAskTextRequest) -> QaAskTextResponse:
        captured["session_id"] = request.session_id
        captured["courseware_id"] = request.courseware_id
        captured["page_index"] = request.page_index
        captured["top_k"] = request.top_k
        return QaAskTextResponse(
            answer="根据课件第 3 页，这是一个关于递归终止条件的问题。",
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


def test_qa_stream_endpoint_returns_delta_and_done(monkeypatch):
    monkeypatch.setattr(
        qa_api,
        "stream_answer_events",
        lambda request: iter([{"type": "delta", "content": f"answer:{request.courseware_id}"}]),
    )

    lines = _collect_sse_lines("/python/v1/qa/stream?coursewareId=cware_stream_1&question=流式输出")

    assert lines[0] == 'data: {"type": "delta", "content": "answer:cware_stream_1"}'
    assert lines[-1] == 'data: {"type": "done"}'


def test_qa_stream_endpoint_returns_error_and_done_when_rag_service_fails(monkeypatch):
    def broken_stream(_request):
        raise RuntimeError("boom")

    monkeypatch.setattr(qa_api, "stream_answer_events", broken_stream)

    lines = _collect_sse_lines("/python/v1/qa/stream?coursewareId=cware_stream_2&question=异常场景")

    assert any('"type": "error"' in line for line in lines)
    assert lines[-1] == 'data: {"type": "done"}'
