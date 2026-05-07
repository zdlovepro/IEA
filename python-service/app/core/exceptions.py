from __future__ import annotations

from dataclasses import dataclass


PARAM_ERROR = 40001
BUSINESS_VALIDATION_FAILED = 40002
INTERNAL_ERROR = 50001
NOT_IMPLEMENTED = 50101
DOWNSTREAM_SERVICE_ERROR = 50201
THIRD_PARTY_SERVICE_ERROR = 50202


@dataclass
class AppException(Exception):
    code: int
    message: str
    status_code: int = 200

    def __str__(self) -> str:
        return self.message


class PythonServiceException(AppException):
    def __init__(self, message: str, code: int = INTERNAL_ERROR, status_code: int = 200) -> None:
        super().__init__(code=code, message=message, status_code=status_code)


class ModelOutputException(AppException):
    def __init__(self, message: str = "模型输出格式异常", status_code: int = 200) -> None:
        super().__init__(code=THIRD_PARTY_SERVICE_ERROR, message=message, status_code=status_code)


class VectorStoreException(AppException):
    def __init__(self, message: str = "向量服务不可用", status_code: int = 200) -> None:
        super().__init__(code=DOWNSTREAM_SERVICE_ERROR, message=message, status_code=status_code)
