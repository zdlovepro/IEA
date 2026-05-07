from __future__ import annotations

from app.schemas.chunk import TextChunk
from app.services import text_chunker as text_chunker_module
from app.services.text_chunker import TextChunkerService, split_chinese_sentences


def test_chunk_courseware_page_short_text_returns_single_structured_chunk():
    service = TextChunkerService(chunk_size=80, chunk_overlap=10)

    chunks = service.chunk_courseware_page(
        page_index=1,
        original_text="这是第一页的简短内容。",
        courseware_id="cware_demo",
        title="第一页",
        knowledge_points=["重点一"],
    )

    assert len(chunks) == 1
    chunk = chunks[0]
    assert set(chunk.keys()) == {
        "chunk_id",
        "courseware_id",
        "page_index",
        "chunk_index",
        "text",
        "content",
        "sentences",
        "metadata",
    }
    assert chunk["chunk_id"] == "cware_demo_p001_c000"
    assert chunk["courseware_id"] == "cware_demo"
    assert chunk["page_index"] == 1
    assert chunk["chunk_index"] == 0
    assert chunk["text"] == chunk["content"]
    assert chunk["sentences"]
    assert chunk["metadata"]["page_index"] == 1
    assert chunk["metadata"]["sentence_count"] == len(chunk["sentences"])
    assert chunk["metadata"]["char_count"] == len(chunk["text"])
    assert chunk["metadata"]["title"] == "第一页"
    assert chunk["metadata"]["knowledge_points"] == ["重点一"]
    TextChunk.model_validate(chunk)


def test_split_chinese_sentences_handles_long_chinese_text():
    text = "这是一个很长的句子，里面包含很多说明：先介绍背景，再解释原因，最后补充结论；下一句继续说明整体思路。"

    sentences = split_chinese_sentences(text, max_sentence_length=12)

    assert len(sentences) >= 4
    assert all(sentence.strip() for sentence in sentences)
    assert any("背景" in sentence for sentence in sentences)
    assert any("结论" in sentence for sentence in sentences)


def test_chunk_courseware_pages_supports_multiple_page_fields_and_resets_chunk_index():
    service = TextChunkerService(chunk_size=24, chunk_overlap=6)
    pages = [
        {
            "page_index": 1,
            "title": "第一页",
            "text": "第一页第一句。第一页第二句。第一页第三句。第一页第四句。",
            "knowledge_points": ["概念A"],
        },
        {
            "page_index": 2,
            "content": "第二页内容第一句。第二页内容第二句。",
            "keywords": ["概念B"],
        },
        {
            "page_index": 3,
            "text": "   ",
        },
    ]

    chunks = service.chunk_courseware_pages("cware_multi", pages)

    page1_chunks = [chunk for chunk in chunks if chunk["page_index"] == 1]
    page2_chunks = [chunk for chunk in chunks if chunk["page_index"] == 2]
    page3_chunks = [chunk for chunk in chunks if chunk["page_index"] == 3]

    assert len(page1_chunks) >= 2
    assert len(page2_chunks) >= 1
    assert not page3_chunks
    assert [chunk["chunk_index"] for chunk in page1_chunks] == list(range(len(page1_chunks)))
    assert page2_chunks[0]["chunk_index"] == 0
    assert any(chunk["metadata"]["knowledge_points"] == ["概念A"] for chunk in page1_chunks)
    assert any(chunk["metadata"]["knowledge_points"] == ["概念B"] for chunk in page2_chunks)


def test_chunk_courseware_page_handles_super_long_text_without_punctuation():
    service = TextChunkerService(chunk_size=25, chunk_overlap=5)
    long_text = "超长文本没有标点" * 20

    chunks = service.chunk_courseware_page(2, long_text, "cware_long")

    assert len(chunks) > 1
    assert all(chunk["sentences"] for chunk in chunks)
    assert all(chunk["metadata"]["page_index"] == 2 for chunk in chunks)
    assert all(chunk["metadata"]["char_count"] == len(chunk["text"]) for chunk in chunks)


def test_chunk_ids_are_stable_for_same_input():
    service = TextChunkerService(chunk_size=26, chunk_overlap=4)
    text = "稳定 ID 第一段。稳定 ID 第二段。稳定 ID 第三段。"

    first_chunks = service.chunk_courseware_page(4, text, "cware_stable")
    second_chunks = service.chunk_courseware_page(4, text, "cware_stable")

    assert [chunk["chunk_id"] for chunk in first_chunks] == [chunk["chunk_id"] for chunk in second_chunks]


def test_chunking_still_works_without_jieba(monkeypatch):
    monkeypatch.setattr(text_chunker_module, "jieba", None)
    text = "没有分词依赖时也要继续工作" * 10

    sentences = split_chinese_sentences(text, max_sentence_length=9)
    chunks = TextChunkerService(chunk_size=18, chunk_overlap=3).chunk_courseware_page(
        page_index=5,
        original_text=text,
        courseware_id="cware_no_jieba",
    )

    assert sentences
    assert len(chunks) > 1
    assert all(chunk["sentences"] for chunk in chunks)
