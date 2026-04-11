from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    PROJECT_NAME: str = "AI Interactive Lecture API"
    
    # Vector DB Config (Milvus)
    MILVUS_URI: str = "http://localhost:19530" # 恢复为 Docker 模式的默认地址
    MILVUS_USER: str = ""
    MILVUS_PASSWORD: str = ""
    MILVUS_DB_NAME: str = "default"
    COLLECTION_NAME: str = "lecture_knowledge"
    
    # Embedding Model Config
    EMBEDDING_MODEL_NAME: str = "BAAI/bge-large-zh-v1.5" # 推荐使用 BGE 等中文友好的轻量级模型
    EMBEDDING_DIM_SIZE: int = 1024

    class Config:
        env_file = ".env"

settings = Settings()