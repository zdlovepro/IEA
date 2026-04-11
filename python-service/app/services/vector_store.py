import os
os.environ["HF_ENDPOINT"] = "https://hf-mirror.com"  # 使用国内镜像加速下载
from typing import List, Dict, Any, Optional
from pymilvus import connections, utility, FieldSchema, CollectionSchema, DataType, Collection
from langchain_community.embeddings import HuggingFaceBgeEmbeddings
from app.core.config import settings

class VectorStoreManager:
    def __init__(self):
        self.collection_name = settings.COLLECTION_NAME
        self.dim = settings.EMBEDDING_DIM_SIZE
        self.embeddings = HuggingFaceBgeEmbeddings(
            model_name=settings.EMBEDDING_MODEL_NAME,
            model_kwargs={'device': 'cpu'},  # 如果有GPU可改为 'cuda'
            encode_kwargs={'normalize_embeddings': True}
        )
        self.collection = None
        self._connect()

    def _connect(self):
        """连接到Milvus/向量数据库"""
        print(f"Connecting to Milvus at {settings.MILVUS_URI}...")
        try:
            # 建立连接，Milvus 2.3+ 支持直接使用本地文件（Milvus Lite）或 Docker
            connections.connect(
                alias="default", 
                uri=settings.MILVUS_URI,
                user=settings.MILVUS_USER,
                password=settings.MILVUS_PASSWORD,
                db_name=settings.MILVUS_DB_NAME
            )
            self._init_collection()
            print("Connected to Milvus successfully.")
        except Exception as e:
            print(f"Failed to connect to Milvus: {e}")
            raise e

    def _init_collection(self):
        """初始化知识库集合"""
        if utility.has_collection(self.collection_name):
            self.collection = Collection(self.collection_name)
            self.collection.load()
            print(f"Loaded existing collection: {self.collection_name}")
            return

        print(f"Creating new collection: {self.collection_name}")
        
        # 1. 定义字段 Schema
        fields = [
            FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=True),
            FieldSchema(name="courseware_id", dtype=DataType.VARCHAR, max_length=128, description="课件ID"),
            FieldSchema(name="node_id", dtype=DataType.VARCHAR, max_length=128, description="授课片段节点ID"),
            FieldSchema(name="content", dtype=DataType.VARCHAR, max_length=65535, description="原始文本内容"),
            FieldSchema(name="vector", dtype=DataType.FLOAT_VECTOR, dim=self.dim, description="文本嵌入向量")
        ]
        schema = CollectionSchema(fields=fields, description="Courseware knowledge fragments")
        
        # 2. 创建集合
        self.collection = Collection(name=self.collection_name, schema=schema)
        
        # 3. 创建 IVF_FLAT 索引以加速检索
        index_params = {
            "metric_type": "IP", # Inner Product (因为已经做了normalize)
            "index_type": "IVF_FLAT",
            "params": {"nlist": 1024}
        }
        self.collection.create_index(field_name="vector", index_params=index_params)
        self.collection.load()

    def embed_text(self, text: str) -> List[float]:
        """将单个文本转为向量"""
        return self.embeddings.embed_query(text)

    def embed_batch(self, texts: List[str]) -> List[List[float]]:
        """批量文本向量化"""
        return self.embeddings.embed_documents(texts)

    def insert_documents(self, courseware_id: str, documents: List[Dict[str, Any]]):
        """
        向向量库插入文档片段
        :param courseware_id: 课件唯一标识
        :param documents: 包含 'node_id' 和 'content' 的字典列表
        """
        if not documents:
            return 0
            
        texts = [doc['content'] for doc in documents]
        vectors = self.embed_batch(texts)
        
        entities = [
            [courseware_id] * len(documents),
            [doc.get('node_id', '') for doc in documents],
            texts,
            vectors
        ]
        
        res = self.collection.insert(entities)
        # Flush/load 确保即时可查
        self.collection.flush()
        print(f"Inserted {len(documents)} fragments for courseware {courseware_id}")
        return len(res.primary_keys)

    def search_similar(self, query: str, courseware_id: str = None, top_k: int = 3) -> List[Dict[str, Any]]:
        """
        检索相似文本片段
        :param query: 检索提问
        :param courseware_id: 限定特定课件查询 (可选)
        :param top_k: 返回条数
        """
        query_vector = self.embed_text(query)
        
        search_params = {"metric_type": "IP", "params": {"nprobe": 10}}
        expr = f"courseware_id == '{courseware_id}'" if courseware_id else None
        
        results = self.collection.search(
            data=[query_vector], 
            anns_field="vector", 
            param=search_params,
            limit=top_k, 
            expr=expr,
            output_fields=["courseware_id", "node_id", "content"]
        )
        
        out = []
        for hits in results:
            for hit in hits:
                out.append({
                    "id": hit.id,
                    "distance": hit.distance, # 相似度得分
                    "courseware_id": hit.entity.get("courseware_id"),
                    "node_id": hit.entity.get("node_id"),
                    "content": hit.entity.get("content")
                })
        return out

# 全局单例
vector_store = None

def get_vector_store():
    global vector_store
    if vector_store is None:
        vector_store = VectorStoreManager()
    return vector_store