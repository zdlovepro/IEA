from pathlib import Path
from typing import Any


def build_parse_payload(courseware_id: str, file_name: str | None, content_type: str | None) -> dict[str, Any]:
    topic = Path(file_name or courseware_id).stem or courseware_id
    file_kind = _detect_file_kind(file_name=file_name, content_type=content_type)

    segments = [
        {
            "pageIndex": 1,
            "title": "课程导入",
            "content": f"这份{file_kind}课件《{topic}》首先会介绍主题背景，并说明本节内容的学习目标。",
            "knowledgePoints": [topic, "学习目标"],
        },
        {
            "pageIndex": 2,
            "title": "核心概念",
            "content": "中间部分会围绕关键概念、典型示例和适用场景展开，帮助学生形成完整理解。",
            "knowledgePoints": ["核心概念", "典型示例"],
        },
        {
            "pageIndex": 3,
            "title": "总结回顾",
            "content": "最后会回顾重点知识，并提示如何把本节内容迁移到练习或实际任务中。",
            "knowledgePoints": ["知识总结", "迁移应用"],
        },
    ]

    return {
        "pages": len(segments),
        "outline": [segment["title"] for segment in segments],
        "segments": segments,
    }


def _detect_file_kind(file_name: str | None, content_type: str | None) -> str:
    suffix = Path(file_name or "").suffix.lower()
    if suffix == ".pdf" or content_type == "application/pdf":
        return "PDF"
    if suffix in {".ppt", ".pptx"}:
        return "PPT"
    return "教学"
