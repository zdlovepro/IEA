from typing import List, Dict, Any
from langchain.text_splitter import RecursiveCharacterTextSplitter
from app.utils.logger import logger

class TextChunkerService:
    def __init__(self, chunk_size: int = 500, chunk_overlap: int = 50):
        """
        初始化文本切片服务
        :param chunk_size: 每个切片的最大字符数
        :param chunk_overlap: 相邻切片之间的重叠字符数，用于保留上下文
        """
        # 使用 RecursiveCharacterTextSplitter 按段落、句子、单词递归切分，尽量保证语义完整
        self.text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=chunk_size,
            chunk_overlap=chunk_overlap,
            length_function=len,
            separators=["\n\n", "\n", "。", "！", "？", "；", ".", " ", ""]
        )

    def chunk_courseware_page(self, page_index: int, original_text: str, courseware_id: str = "") -> List[Dict[str, Any]]:
        """
        针对课件单页文本进行切片，并注入元数据 (Metadata)
        :param page_index: 当前页码
        :param original_text: 当前页的原文本
        :param courseware_id: 课件ID（选填，方便向量检索隔离）
        :return: 包含 text 和 metadata 的字典列表
        """
        if not original_text or not original_text.strip():
            logger.warning(f"课件 ID: {courseware_id} 第 {page_index} 页文本为空，跳过切片。")
            return []

        logger.info(f"正在对课件 ID: {courseware_id} 第 {page_index} 页文本进行分块，文本长度: {len(original_text)} 字符")
        
        # 将文本切分为字符串列表
        chunks = self.text_splitter.split_text(original_text)
        
        # 为每个 chunk 添加 Metadata (页码信息)
        document_chunks = []
        for i, chunk in enumerate(chunks):
            document_chunks.append({
                "text": chunk,
                "metadata": {
                    "courseware_id": courseware_id,
                    "page_index": page_index,
                    "chunk_index": i
                }
            })
            
        logger.info(f"第 {page_index} 页切割完成，共产生 {len(document_chunks)} 个 Chunks。")
        return document_chunks
