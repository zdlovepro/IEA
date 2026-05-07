from __future__ import annotations

from typing import Optional

from langchain.schema import BaseMessage
from langchain_community.chat_models import ChatOpenAI

from app.core.config import settings
from app.core.exceptions import ModelOutputException, PythonServiceException, THIRD_PARTY_SERVICE_ERROR
from app.utils.logger import logger


class LLMClient:
    def __init__(self) -> None:
        if not settings.LLM_API_KEY:
            raise PythonServiceException("LLM 服务未配置", code=THIRD_PARTY_SERVICE_ERROR)

        self._chat_model = ChatOpenAI(
            openai_api_key=settings.LLM_API_KEY,
            openai_api_base=settings.LLM_API_BASE,
            model_name=settings.LLM_MODEL_NAME,
            temperature=settings.LLM_TEMPERATURE,
            max_tokens=settings.LLM_MAX_TOKENS,
            request_timeout=settings.LLM_TIMEOUT,
            max_retries=2,
        )

    @property
    def chat_model(self) -> ChatOpenAI:
        return self._chat_model

    def invoke(self, messages: list[BaseMessage]) -> Optional[str]:
        logger.info("Invoking LLM. messageCount=%s model=%s", len(messages), settings.LLM_MODEL_NAME)
        response = self._chat_model.invoke(messages)
        content = response.content
        if not content:
            raise ModelOutputException("模型未返回有效内容")
        logger.info("LLM invocation completed. outputLength=%s", len(content))
        return content


_llm_client: Optional[LLMClient] = None


def get_llm_client() -> LLMClient:
    global _llm_client
    if _llm_client is None:
        _llm_client = LLMClient()
    return _llm_client
