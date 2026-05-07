from __future__ import annotations

from pathlib import Path

import pdfplumber

from app.schemas.parse import PageContent, ParseResult
from app.utils.logger import logger


def parse_pdf(file_path: str, courseware_id: str) -> ParseResult:
    path = Path(file_path)
    if not path.exists():
        raise FileNotFoundError(f"文件不存在：{file_path}")
    if path.suffix.lower() != ".pdf":
        raise ValueError(f"不支持的文件格式：{path.suffix}，期望 .pdf")

    logger.info("Start parsing PDF. coursewareId=%s path=%s", courseware_id, file_path)
    pages: list[PageContent] = []

    with pdfplumber.open(str(path)) as pdf:
        for index, page in enumerate(pdf.pages, start=1):
            text = (page.extract_text() or "").strip()
            image_placeholders = [
                f"[图片:page_{index}_img_{image_index}]"
                for image_index, _image in enumerate(page.images, start=1)
            ]
            pages.append(
                PageContent(
                    page_index=index,
                    text=text,
                    notes="",
                    image_placeholders=image_placeholders,
                    formula_placeholders=[],
                )
            )

    result = ParseResult(
        courseware_id=courseware_id,
        file_type="pdf",
        total_pages=len(pages),
        pages=pages,
    )
    logger.info("PDF parsing completed. coursewareId=%s totalPages=%s", courseware_id, result.total_pages)
    return result
