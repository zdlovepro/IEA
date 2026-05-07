from __future__ import annotations

from app.api.v1 import parse as parse_api


def test_health_returns_code_zero(request_app):
    response = request_app("GET", "/python/v1/health")

    assert response.status_code == 200
    assert response.json() == {
        "code": 0,
        "message": "success",
        "data": {
            "service": "AI Interactive Lecture API",
            "status": "UP",
        },
    }


def test_parse_request_accepts_camel_case_fields(request_app, monkeypatch):
    captured = {}

    def fake_parse_courseware_file(request):
        captured["courseware_id"] = request.courseware_id
        captured["file_name"] = request.file_name
        captured["content_type"] = request.content_type
        return {"pages": 1, "outline": ["课程导入"], "segments": []}

    monkeypatch.setattr(parse_api, "parse_courseware_file", fake_parse_courseware_file)

    response = request_app(
        "POST",
        "/python/v1/parse",
        json={
            "coursewareId": "cware_123",
            "storage": "local",
            "key": "cware_123/demo.pptx",
            "fileName": "demo.pptx",
            "contentType": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        },
    )

    assert response.status_code == 200
    assert response.json()["code"] == 0
    assert captured == {
        "courseware_id": "cware_123",
        "file_name": "demo.pptx",
        "content_type": "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    }


def test_unknown_exception_returns_base_response(request_app, monkeypatch):
    def boom(_request):
        raise RuntimeError("sensitive internal detail")

    monkeypatch.setattr(parse_api, "parse_courseware_file", boom)

    response = request_app(
        "POST",
        "/python/v1/parse",
        json={
            "coursewareId": "cware_500",
            "storage": "local",
            "key": "cware_500/demo.pdf",
            "fileName": "demo.pdf",
            "contentType": "application/pdf",
        },
    )

    body = response.json()
    assert response.status_code == 200
    assert body["code"] == 50001
    assert body["data"] is None
    assert body["message"] == "服务内部错误，请稍后重试"
    assert "sensitive internal detail" not in body["message"]
