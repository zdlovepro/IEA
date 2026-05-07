from __future__ import annotations

import re
from typing import Any

from langchain.text_splitter import RecursiveCharacterTextSplitter

from app.schemas.chunk import ChunkMetadata, TextChunk
from app.utils.logger import logger

try:
    import jieba
except ImportError:  # pragma: no cover - covered by runtime fallback tests
    jieba = None

_PRIMARY_SENTENCE_DELIMITERS = r"[。？！!；;…\n]+"
_SECONDARY_SENTENCE_DELIMITERS = r"[：:，,、—]+"


def split_chinese_sentences(text: str, max_sentence_length: int = 120) -> list[str]:
    normalized_text = _normalize_text(text)
    if not normalized_text:
        return []

    effective_max_length = max(1, max_sentence_length)
    coarse_sentences = _split_with_delimiters(normalized_text, _PRIMARY_SENTENCE_DELIMITERS)
    if not coarse_sentences:
        coarse_sentences = [normalized_text]

    result: list[str] = []
    for sentence in coarse_sentences:
        result.extend(_split_sentence_fragment(sentence, effective_max_length))

    return [sentence for sentence in (item.strip() for item in result) if sentence]


class TextChunkerService:
    def __init__(self, chunk_size: int = 500, chunk_overlap: int = 50):
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            length_function=len,
            separators=["\n\n", "\n", "。", "！", "？", "；", "：", "，", "、", " ", ""],
        )

    def chunk_courseware_page(
        self,
        page_index: int,
        original_text: str,
        courseware_id: str = "",
        title: str | None = None,
        knowledge_points: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        normalized_text = _normalize_text(original_text)
        if not normalized_text:
            logger.warning(
                "Skip chunking empty page. coursewareId=%s pageIndex=%s",
                courseware_id,
                page_index,
            )
            return []

        logger.info(
            "Chunking page. coursewareId=%s pageIndex=%s textLength=%s",
            courseware_id,
            page_index,
            len(normalized_text),
        )

        sentence_max_length = max(1, min(self.chunk_size, 120))
        sentences = split_chinese_sentences(normalized_text, max_sentence_length=sentence_max_length)
        if not sentences:
            sentences = [normalized_text]

        chunk_sentence_groups = self._group_sentences_into_chunks(sentences)
        cleaned_knowledge_points = _clean_string_list(knowledge_points)
        document_chunks: list[dict[str, Any]] = []

        for chunk_index, chunk_sentences in enumerate(chunk_sentence_groups):
            chunk_text = "\n".join(chunk_sentences).strip()
            if not chunk_text:
                continue

            metadata_model = ChunkMetadata(
                courseware_id=courseware_id,
                page_index=page_index,
                chunk_index=chunk_index,
                source="courseware",
                sentence_count=len(chunk_sentences),
                char_count=len(chunk_text),
                title=title.strip() if title and title.strip() else None,
                knowledge_points=cleaned_knowledge_points or None,
            )
            chunk_model = TextChunk(
                chunk_id=self._build_chunk_id(courseware_id, page_index, chunk_index),
                courseware_id=courseware_id,
                page_index=page_index,
                chunk_index=chunk_index,
                text=chunk_text,
                content=chunk_text,
                sentences=chunk_sentences,
                metadata=metadata_model,
            )
            document_chunks.append(chunk_model.model_dump(mode="python"))

        logger.info(
            "Chunked page finished. coursewareId=%s pageIndex=%s chunkCount=%s",
            courseware_id,
            page_index,
            len(document_chunks),
        )
        return document_chunks

    def chunk_courseware_pages(self, courseware_id: str, pages: list[dict[str, Any]]) -> list[dict[str, Any]]:
        if not pages:
            return []

        all_chunks: list[dict[str, Any]] = []
        for page in pages:
            page_index = page.get("page_index")
            if not isinstance(page_index, int) or page_index <= 0:
                logger.warning(
                    "Skip page with invalid page index. coursewareId=%s rawPage=%s",
                    courseware_id,
                    page,
                )
                continue

            page_text = _resolve_page_text(page)
            if not page_text:
                logger.info(
                    "Skip empty courseware page during batch chunking. coursewareId=%s pageIndex=%s",
                    courseware_id,
                    page_index,
                )
                continue

            title = page.get("title")
            knowledge_points = _resolve_knowledge_points(page)
            all_chunks.extend(
                self.chunk_courseware_page(
                    page_index=page_index,
                    original_text=page_text,
                    courseware_id=courseware_id,
                    title=title if isinstance(title, str) else None,
                    knowledge_points=knowledge_points,
                )
            )

        logger.info(
            "Chunked courseware pages. coursewareId=%s pageCount=%s chunkCount=%s",
            courseware_id,
            len(pages),
            len(all_chunks),
        )
        return all_chunks

    def _group_sentences_into_chunks(self, sentences: list[str]) -> list[list[str]]:
        if not sentences:
            return []

        chunk_groups: list[list[str]] = []
        current_chunk: list[str] = []
        current_length = 0

        for sentence in sentences:
            for fragment in self._split_oversized_sentence(sentence):
                fragment = fragment.strip()
                if not fragment:
                    continue

                if current_chunk and current_length + len(fragment) > self.chunk_size:
                    chunk_groups.append(current_chunk.copy())
                    current_chunk = self._build_overlap_sentences(current_chunk)
                    current_length = sum(len(item) for item in current_chunk)

                    while current_chunk and current_length + len(fragment) > self.chunk_size:
                        current_chunk.pop(0)
                        current_length = sum(len(item) for item in current_chunk)

                current_chunk.append(fragment)
                current_length += len(fragment)

        if current_chunk:
            chunk_groups.append(current_chunk.copy())

        return chunk_groups

    def _split_oversized_sentence(self, sentence: str) -> list[str]:
        cleaned_sentence = sentence.strip()
        if not cleaned_sentence:
            return []
        if len(cleaned_sentence) <= self.chunk_size:
            return [cleaned_sentence]

        fragments = [
            fragment.strip()
            for fragment in self.text_splitter.split_text(cleaned_sentence)
            if fragment.strip()
        ]
        if fragments:
            return fragments
        return _split_by_length(cleaned_sentence, self.chunk_size)

    def _build_overlap_sentences(self, sentences: list[str]) -> list[str]:
        if self.chunk_overlap <= 0 or not sentences:
            return []

        overlap_sentences: list[str] = []
        overlap_length = 0
        for sentence in reversed(sentences):
            overlap_sentences.insert(0, sentence)
            overlap_length += len(sentence)
            if overlap_length >= self.chunk_overlap:
                break
        return overlap_sentences

    @staticmethod
    def _build_chunk_id(courseware_id: str, page_index: int, chunk_index: int) -> str:
        safe_courseware_id = courseware_id.strip() if courseware_id and courseware_id.strip() else "courseware"
        return f"{safe_courseware_id}_p{page_index:03d}_c{chunk_index:03d}"


def _normalize_text(text: str | None) -> str:
    if not text:
        return ""

    normalized = text.replace("\r\n", "\n").replace("\r", "\n")
    normalized = re.sub(r"[ \t\f\v]+", " ", normalized)
    normalized = re.sub(r"\n{2,}", "\n", normalized)
    return normalized.strip()


def _split_sentence_fragment(sentence: str, max_sentence_length: int) -> list[str]:
    cleaned_sentence = sentence.strip()
    if not cleaned_sentence:
        return []
    if len(cleaned_sentence) <= max_sentence_length:
        return [cleaned_sentence]

    sub_sentences = _split_with_delimiters(cleaned_sentence, _SECONDARY_SENTENCE_DELIMITERS)
    if not sub_sentences:
        return _split_by_length(cleaned_sentence, max_sentence_length)

    result: list[str] = []
    for sub_sentence in sub_sentences:
        normalized_sub_sentence = sub_sentence.strip()
        if not normalized_sub_sentence:
            continue
        if len(normalized_sub_sentence) <= max_sentence_length:
            result.append(normalized_sub_sentence)
        else:
            result.extend(_split_by_length(normalized_sub_sentence, max_sentence_length))
    return result


def _split_with_delimiters(text: str, delimiter_pattern: str) -> list[str]:
    parts = re.split(f"({delimiter_pattern})", text)
    if len(parts) == 1:
        single_part = parts[0].strip()
        return [single_part] if single_part else []

    result: list[str] = []
    for index in range(0, len(parts), 2):
        content = parts[index]
        delimiter = parts[index + 1] if index + 1 < len(parts) else ""
        segment = f"{content}{delimiter}".strip()
        if segment:
            result.append(segment)
    return result


def _split_by_length(text: str, max_length: int) -> list[str]:
    cleaned_text = text.strip()
    if not cleaned_text:
        return []
    if len(cleaned_text) <= max_length:
        return [cleaned_text]

    if jieba is not None:
        chunks = _split_by_jieba(cleaned_text, max_length)
        if chunks:
            return chunks

    return _split_fixed_length(cleaned_text, max_length)


def _split_by_jieba(text: str, max_length: int) -> list[str]:
    result: list[str] = []
    current_chunk = ""

    for word in jieba.cut(text):
        if not word:
            continue
        if len(word) > max_length:
            if current_chunk.strip():
                result.append(current_chunk.strip())
                current_chunk = ""
            result.extend(_split_fixed_length(word, max_length))
            continue

        if current_chunk and len(current_chunk) + len(word) > max_length:
            result.append(current_chunk.strip())
            current_chunk = word
        else:
            current_chunk += word

    if current_chunk.strip():
        result.append(current_chunk.strip())

    return result


def _split_fixed_length(text: str, max_length: int) -> list[str]:
    return [
        text[index:index + max_length].strip()
        for index in range(0, len(text), max_length)
        if text[index:index + max_length].strip()
    ]


def _resolve_page_text(page: dict[str, Any]) -> str:
    for field_name in ("text", "text_content", "content"):
        value = page.get(field_name)
        if isinstance(value, str) and value.strip():
            return value
    return ""


def _resolve_knowledge_points(page: dict[str, Any]) -> list[str]:
    knowledge_points = _clean_string_list(page.get("knowledge_points"))
    if knowledge_points:
        return knowledge_points
    return _clean_string_list(page.get("keywords"))


def _clean_string_list(value: Any) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        return [value.strip()] if value.strip() else []
    if isinstance(value, list):
        return [item.strip() for item in value if isinstance(item, str) and item.strip()]
    return []
