from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter

from app.schemas.parse import BaseResponse, ParseRequest, ParseResult, error_response, success_response
from app.utils.logger import logger

router = APIRouter(tags=["parse"])


@router.post("/parse", response_model=BaseResponse, summary="Parse courseware")
async def parse_courseware(request: ParseRequest) -> BaseResponse:
    logger.info(
        "Received parse request courseware_id=%s storage=%s key=%s file_name=%s",
        request.courseware_id,
        request.normalized_storage,
        request.key,
        request.file_name,
    )

    try:
        if request.normalized_storage == "local":
            local_path = _resolve_local_path(request)
            if local_path is None:
                target = request.key or request.preferred_name or "<unknown>"
                return error_response(40002, f"local courseware file not found: {target}")

            result = _parse_local_file(local_path, request)
            return success_response(_build_contract_payload(result, request.preferred_name))

        if request.normalized_storage == "minio":
            return error_response(
                50101,
                f"minio courseware parsing is not implemented yet, key={request.key or '<empty>'}",
            )

        return error_response(40001, f"unsupported storage: {request.storage}")
    except FileNotFoundError as exc:
        logger.warning("Parse source not found for courseware_id=%s: %s", request.courseware_id, exc)
        return error_response(40002, str(exc))
    except ValueError as exc:
        logger.warning("Unsupported parse source for courseware_id=%s: %s", request.courseware_id, exc)
        return error_response(40001, str(exc))
    except Exception as exc:  # noqa: BLE001
        logger.exception("Parse request failed for courseware_id=%s", request.courseware_id)
        return error_response(50001, f"parse service error: {exc}")


def _parse_local_file(local_path: Path, request: ParseRequest) -> ParseResult:
    suffix = _detect_suffix(local_path, request)
    if suffix == ".pptx":
        from app.services.ppt_parser import parse_pptx

        return parse_pptx(str(local_path), request.courseware_id)
    if suffix == ".pdf":
        from app.services.pdf_parser import parse_pdf

        return parse_pdf(str(local_path), request.courseware_id)
    raise ValueError(f"unsupported file type: {suffix}")


def _build_contract_payload(result: ParseResult, preferred_name: str | None) -> dict[str, object]:
    outline: list[str] = []
    segments: list[dict[str, object]] = []
    default_topic = Path(preferred_name or result.courseware_id).stem or result.courseware_id

    for page in result.pages:
        title = _derive_title(page, default_topic)
        content = _merge_page_content(page)
        outline.append(title)
        segments.append(
            {
                "pageIndex": page.page_index,
                "title": title,
                "content": content,
                "knowledgePoints": _derive_knowledge_points(title, default_topic),
            }
        )

    return {
        "pages": result.total_pages,
        "outline": outline,
        "segments": segments,
    }


def _derive_title(page, default_topic: str) -> str:
    first_line = next((line.strip() for line in page.text.splitlines() if line.strip()), "")
    if first_line:
        return first_line[:48]
    if page.notes:
        note_line = next((line.strip() for line in page.notes.splitlines() if line.strip()), "")
        if note_line:
            return note_line[:48]
    return f"{default_topic}-第{page.page_index}页"


def _merge_page_content(page) -> str:
    parts = [part.strip() for part in (page.text, page.notes) if part and part.strip()]
    if parts:
        return "\n".join(parts)
    return f"第{page.page_index}页暂无可提取文本内容。"


def _derive_knowledge_points(title: str, default_topic: str) -> list[str]:
    points = [default_topic]
    if title and title != default_topic:
        points.append(title)
    return points


def _resolve_local_path(request: ParseRequest) -> Path | None:
    for candidate in _candidate_local_paths(request):
        if candidate.exists() and candidate.is_file():
            return candidate
    return None


def _candidate_local_paths(request: ParseRequest) -> list[Path]:
    candidates: list[Path] = []

    if request.key:
        key_path = Path(request.key).expanduser()
        if key_path.is_absolute():
            candidates.append(key_path.resolve())
        else:
            candidates.append((_courseware_root() / request.key).resolve())

    if request.file_name:
        candidates.append((_courseware_root() / request.courseware_id / request.file_name).resolve())

    if request.file_path:
        legacy_path = Path(request.file_path).expanduser()
        if legacy_path.is_absolute():
            candidates.append(legacy_path.resolve())

    return candidates


def _courseware_root() -> Path:
    return _repo_root() / "backend" / "data" / "courseware"


def _repo_root() -> Path:
    return Path(__file__).resolve().parents[4]


def _detect_suffix(local_path: Path, request: ParseRequest) -> str:
    if local_path.suffix:
        return local_path.suffix.lower()

    preferred_name = request.preferred_name or ""
    if Path(preferred_name).suffix:
        return Path(preferred_name).suffix.lower()

    if request.content_type == "application/pdf":
        return ".pdf"
    if request.content_type == "application/vnd.openxmlformats-officedocument.presentationml.presentation":
        return ".pptx"
    return ""
