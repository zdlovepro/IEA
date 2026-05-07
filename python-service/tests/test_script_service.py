from __future__ import annotations

from app.clients import llm_client as llm_client_module
from app.core.config import settings
from app.schemas.script import PageContent, ScriptGenerateRequest
from app.services.script_service import extract_json_payload, generate_script


def _build_request() -> ScriptGenerateRequest:
    return ScriptGenerateRequest(
        courseware_id="cware_script_1",
        courseware_name="递归示例",
        subject="计算机科学",
        pages=[
            PageContent(
                page_index=1,
                title="递归定义",
                text_content="递归是函数直接或间接调用自身的方法。",
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

    assert result.courseware_id == "cware_script_1"
    assert result.opening
    assert len(result.pages) == 1
    assert result.pages[0].script
    assert result.closing
