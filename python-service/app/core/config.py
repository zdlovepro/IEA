"""
统一配置
========
保存位置：python-service/app/core/config.py
"""

from pydantic import AliasChoices, Field
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    PROJECT_NAME: str = "AI Interactive Lecture API"

    # ---------- Vector DB Config (Milvus) ----------
    MILVUS_URI: str = "http://localhost:19530"
    MILVUS_USER: str = ""
    MILVUS_PASSWORD: str = ""
    MILVUS_DB_NAME: str = "default"
    COLLECTION_NAME: str = "lecture_knowledge"

    # ---------- Embedding Model Config ----------
    EMBEDDING_MODEL_NAME: str = "BAAI/bge-large-zh-v1.5"
    EMBEDDING_DIM_SIZE: int = 1024

    # ---------- LLM Config ----------
    LLM_API_KEY: str = Field(default="", validation_alias=AliasChoices("LLM_API_KEY", "DEEPSEEK_API_KEY"))
    LLM_API_BASE: str = Field(
        default="https://api.deepseek.com",
        validation_alias=AliasChoices("LLM_API_BASE", "DEEPSEEK_API_BASE"),
    )
    LLM_MODEL_NAME: str = Field(
        default="deepseek-v4-pro",
        validation_alias=AliasChoices("LLM_MODEL_NAME", "DEEPSEEK_MODEL_NAME"),
    )
    LLM_TEMPERATURE: float = 0.7
    LLM_MAX_TOKENS: int = 4096
    LLM_TIMEOUT: int = 120
    LLM_REASONING_EFFORT: str = Field(
        default="high",
        validation_alias=AliasChoices("LLM_REASONING_EFFORT", "DEEPSEEK_REASONING_EFFORT"),
    )
    LLM_ENABLE_THINKING: bool = Field(
        default=True,
        validation_alias=AliasChoices("LLM_ENABLE_THINKING", "DEEPSEEK_ENABLE_THINKING"),
    )

    class Config:
        env_file = ".env"


settings = Settings()
