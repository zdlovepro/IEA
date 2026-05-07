"""
PDF 解析服务
============
使用 pdfplumber 读取 .pdf 文件，逐页提取文本内容。
图片阶段二记录占位标识，公式阶段二同样记录占位标识。
阶段五集成 OCR / LaTeX OCR 后补充实际内容。

保存位置：python-service/app/services/pdf_parser.py
"""

from pathlib import Path

import pdfplumber

from app.schemas.parse import PageContent, ParseResult
from app.utils.logger import logger


def parse_pdf(file_path: str, courseware_id: str) -> ParseResult:
    """
    解析 .pdf 文件，返回结构化的 ParseResult。

    处理逻辑
    --------
    1. 逐页遍历 PDF
    2. 提取纯文本（pdfplumber.extract_text）
    3. 检测页面中的图片对象，记录占位标识
    4. 公式检测：pdfplumber 无法原生识别公式区域，
       阶段二暂不做 PDF 公式检测，formula_placeholders 留空，
       阶段五集成 LaTeX OCR（如 Pix2Tex / Nougat）后统一处理

    Parameters
    ----------
    file_path : str
        .pdf 文件的绝对路径。
    courseware_id : str
        课件唯一标识（由 Java 后端传入）。

    Returns
    -------
    ParseResult
        包含每页文本、图片占位符及空公式占位符的解析结果。

    Raises
    ------
    FileNotFoundError
        文件路径不存在时抛出。
    ValueError
        文件非 .pdf 格式时抛出。
    """
    path = Path(file_path)

    # ---- 前置校验 ----
    if not path.exists():
        raise FileNotFoundError(f"文件不存在：{file_path}")
    if path.suffix.lower() != ".pdf":
        raise ValueError(f"不支持的文件格式：{path.suffix}，期望 .pdf")

    logger.info("开始解析 PDF 文件：%s（courseware_id=%s）", file_path, courseware_id)

    pages: list[PageContent] = []

    with pdfplumber.open(str(path)) as pdf:
        for idx, page in enumerate(pdf.pages, start=1):
            # ---- 提取文本 ----
            text = page.extract_text() or ""
            text = text.strip()

            # ---- 提取图片占位标识 ----
            image_placeholders: list[str] = []
            if page.images:
                for img_idx, _img in enumerate(page.images, start=1):
                    image_placeholders.append(
                        f"[图片：page_{idx}_img_{img_idx}]"
                    )

            # ---- 公式占位标识 ----
            # pdfplumber 无法原生检测公式区域，阶段二留空，
            # 阶段五集成 LaTeX OCR 后统一填充。
            formula_placeholders: list[str] = []

            # ---- 组装单页结果 ----
            page_content = PageContent(
                page_index=idx,
                text=text,
                notes="",  # PDF 没有演讲者备注
                image_placeholders=image_placeholders,
                formula_placeholders=formula_placeholders,
            )
            pages.append(page_content)

    result = ParseResult(
        courseware_id=courseware_id,
        file_type="pdf",
        total_pages=len(pages),
        pages=pages,
    )

    logger.info(
        "PDF 解析完成：共 %d 页（courseware_id=%s）",
        result.total_pages,
        courseware_id,
    )
    return result
