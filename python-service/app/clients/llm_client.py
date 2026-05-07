"""
LLM 客户端封装。
统一处理 API Key 注入、超时、重试和异常，不对外暴露底层实现。
"""
import logging
from typing import Optional
from langchain_community.chat_models import ChatOpenAI
from langchain.schema import BaseMessage
from app.core.config import settings

logger = logging.getLogger(__name__)


class LLMClient:
    """
    封装对大语言模型的调用。
    使用 langchain ChatOpenAI，兼容 OpenAI 兼容协议的国产模型（如通义千问、智谱等）。
    """

    def __init__(self):
        if not settings.LLM_API_KEY:
            raise ValueError("LLM_API_KEY 未配置，请在 .env 文件中设置 LLM_API_KEY")

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
        """返回底层 ChatOpenAI 实例，供 LangChain Chain 直接组合使用。"""
        return self._chat_model

    def invoke(self, messages: list[BaseMessage]) -> Optional[str]:
        """
        同步调用大模型，返回模型的纯文本内容。
        网络/超时异常向上抛出，由 Service 层统一处理。
        """
        logger.debug("向大模型发送请求，消息数量: %d", len(messages))
        response = self._chat_model.invoke(messages)
        content = response.content
        logger.debug("大模型返回内容长度: %d", len(content))
        return content


# 模块级单例，避免重复初始化
_llm_client: Optional[LLMClient] = None


def get_llm_client() -> LLMClient:
    """获取 LLM 客户端单例（懒初始化）。"""
    global _llm_client
    if _llm_client is None:
        _llm_client = LLMClient()
    return _llm_client
