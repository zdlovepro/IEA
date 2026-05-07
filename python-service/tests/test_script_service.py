from __future__ import annotations

from app.clients import llm_client as llm_client_module
from app.core.config import settings
from app.schemas.script import PageContent, ScriptGenerateRequest, ScriptGenerateResponse
from app.services import script_service
from app.services.script_service import extract_json_payload, generate_script


def _build_request(
    *,
    subject: str | None = "计算机科学",
    pages: list[PageContent] | None = None,
) -> ScriptGenerateRequest:
    return ScriptGenerateRequest(
        courseware_id="cware_script_1",
        courseware_name="递归示例",
        subject=subject,
        pages=pages
        or [
            PageContent(
                page_index=1,
                title="递归定义",
                text_content="递归是函数直接或间接调用自身的一种方法，通常需要有终止条件来避免无限循环。",
                keywords=["递归", "终止条件"],
            )
        ],
    )


def test_extract_json_payload_from_code_fence():
    raw_output = """```json
{
  "courseware_id": "cware_script_1",
  "opening": "开场",
  "pages": [{"page_index": 1, "script": "讲解", "transition": "过渡"}],
  "closing": "结尾"
}
```"""

    extracted = extract_json_payload(raw_output)

    assert extracted.startswith("{")
    assert '"courseware_id": "cware_script_1"' in extracted


def test_generate_script_falls_back_when_api_key_missing(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "")
    monkeypatch.setattr(llm_client_module, "_llm_client", None)

    result = generate_script(_build_request())

    assert isinstance(result, ScriptGenerateResponse)
    assert result.courseware_id == "cware_script_1"
    assert "递归示例" in result.opening
    assert len(result.pages) == 1
    assert "递归定义" in result.pages[0].script
    assert result.closing


def test_generate_script_uses_general_subject_when_subject_missing(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "")
    monkeypatch.setattr(llm_client_module, "_llm_client", None)

    result = generate_script(_build_request(subject=None))

    assert "通用课程" in result.opening


def test_generate_script_fallback_handles_blank_page_text(monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "")
    monkeypatch.setattr(llm_client_module, "_llm_client", None)

    request = _build_request(
        pages=[
            PageContent(
                page_index=1,
                title="课程导入",
                text_content="   ",
                keywords=["学习目标", "课程导入"],
            )
        ]
    )

    result = generate_script(request)

    assert "原始文字比较少" in result.pages[0].script
    assert "学习目标、课程导入" in result.pages[0].script


def test_generate_script_prompt_contains_total_pages_and_page_content(monkeypatch):
    captured: dict[str, str] = {}

    class _FakeLLMClient:
        def invoke(self, messages):
            captured["system"] = messages[0].content
            captured["human"] = messages[1].content
            return """
            {
              "courseware_id": "ignored-by-parser",
              "opening": "同学们好，我们先来建立整体认识。",
              "pages": [
                {
                  "page_index": 1,
                  "script": "这一页先解释递归的基本定义和终止条件。",
                  "transition": "理解定义后，我们继续看下一页。"
                },
                {
                  "page_index": 2,
                  "script": "这一页进一步说明递归展开时的执行过程。",
                  "transition": "两页内容串起来后，我们就能做总结了。"
                }
              ],
              "closing": "今天的主要内容就梳理到这里。"
            }
            """

    monkeypatch.setattr(settings, "LLM_API_KEY", "test-key")
    monkeypatch.setattr(script_service, "get_llm_client", lambda: _FakeLLMClient())

    request = _build_request(
        subject=None,
        pages=[
            PageContent(
                page_index=1,
                title="递归定义",
                text_content="递归是函数直接或间接调用自身的一种方法。",
                keywords=["递归", "终止条件"],
            ),
            PageContent(
                page_index=2,
                title="执行过程",
                text_content="调用栈会随着递归层级不断展开，再逐层返回。",
                keywords=["调用栈", "返回"],
            ),
        ],
    )

    result = generate_script(request)

    assert isinstance(result, ScriptGenerateResponse)
    assert ScriptGenerateResponse.model_validate(result.model_dump()) == result
    assert "只能输出合法 JSON" in captured["system"]
    assert "总页数：2" in captured["human"]
    assert "学科：通用课程" in captured["human"]
    assert "标题：递归定义" in captured["human"]
    assert "正文：递归是函数直接或间接调用自身的一种方法。" in captured["human"]
    assert "关键词：调用栈、返回" in captured["human"]


def test_generate_script_endpoint_returns_base_response_when_api_key_missing(request_app, monkeypatch):
    monkeypatch.setattr(settings, "LLM_API_KEY", "")
    monkeypatch.setattr(llm_client_module, "_llm_client", None)

    response = request_app(
        "POST",
        "/python/v1/script/generate",
        json={
            "coursewareId": "cware_script_api",
            "coursewareName": "链表示例",
            "subject": "",
            "pages": [
                {
                    "pageIndex": 1,
                    "title": "链表结构",
                    "textContent": "链表由节点组成，每个节点包含数据和指针。",
                    "keywords": ["节点", "指针"],
                }
            ],
        },
    )

    payload = response.json()

    assert response.status_code == 200
    assert payload["code"] == 0
    assert payload["message"] == "success"
    assert payload["data"]["courseware_id"] == "cware_script_api"
    assert payload["data"]["opening"]
    assert payload["data"]["pages"][0]["page_index"] == 1
    assert payload["data"]["pages"][0]["script"]
    assert payload["data"]["closing"]
