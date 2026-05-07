from __future__ import annotations

from typing import Optional

from langchain.schema import BaseMessage
from openai import OpenAI

from app.core.config import settings
from app.core.exceptions import ModelOutputException, PythonServiceException, THIRD_PARTY_SERVICE_ERROR
from app.utils.logger import logger


class LLMClient:
    def __init__(self) -> None:
        if not settings.LLM_API_KEY:
            raise PythonServiceException("LLM 服务未配置", code=THIRD_PARTY_SERVICE_ERROR)

        self._client = OpenAI(
            api_key=settings.LLM_API_KEY,
            base_url=settings.LLM_API_BASE,
        )

    @property
    def client(self) -> OpenAI:
        return self._client

    def invoke(self, messages: list[BaseMessage]) -> Optional[str]:
        logger.info(
            "Invoking LLM. messageCount=%s model=%s reasoningEffort=%s thinkingEnabled=%s",
            len(messages),
            settings.LLM_MODEL_NAME,
            settings.LLM_REASONING_EFFORT,
            settings.LLM_ENABLE_THINKING,
        )

        response = self._client.chat.completions.create(
            model=settings.LLM_MODEL_NAME,
            messages=[_to_openai_message(message) for message in messages],
            stream=False,
            reasoning_effort=settings.LLM_REASONING_EFFORT,
            extra_body=_build_extra_body(),
            timeout=settings.LLM_TIMEOUT,
        )

        content = response.choices[0].message.content if response.choices else None
        if not content:
            raise ModelOutputException("模型未返回有效内容")

        logger.info("LLM invocation completed. outputLength=%s", len(content))
        return content


def _to_openai_message(message: BaseMessage) -> dict[str, str]:
    role = "user"
    if message.type == "system":
        role = "system"
    elif message.type == "ai":
        role = "assistant"

    return {
        "role": role,
        "content": message.content,
    }


def _build_extra_body() -> dict[str, object] | None:
    if not settings.LLM_ENABLE_THINKING:
        return None
    return {"thinking": {"type": "enabled"}}


_llm_client: Optional[LLMClient] = None


def get_llm_client() -> LLMClient:
    global _llm_client
    if _llm_client is None:
        _llm_client = LLMClient()
    return _llm_client
