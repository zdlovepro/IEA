from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api.v1 import router as api_v1_router
from app.core.config import settings
from app.core.exceptions import AppException, INTERNAL_ERROR, PARAM_ERROR
from app.core.logging_middleware import RequestLoggingMiddleware
from app.schemas.common import BaseResponse, error_response, success_response
from app.utils.logger import logger

app = FastAPI(title=settings.PROJECT_NAME)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.add_middleware(RequestLoggingMiddleware)

app.include_router(api_v1_router, prefix="/python/v1")


@app.exception_handler(AppException)
async def handle_app_exception(request: Request, exc: AppException) -> JSONResponse:
    logger.warning(
        "Handled app exception. traceId=%s path=%s code=%s message=%s",
        _trace_id(request),
        request.url.path,
        exc.code,
        exc.message,
    )
    return _error_json_response(request, exc.code, exc.message, exc.status_code)


@app.exception_handler(RequestValidationError)
async def handle_validation_exception(request: Request, exc: RequestValidationError) -> JSONResponse:
    first_error = exc.errors()[0] if exc.errors() else None
    location = ".".join(str(item) for item in first_error.get("loc", [])) if first_error else "request"
    detail = first_error.get("msg", "请求参数校验失败") if first_error else "请求参数校验失败"
    message = f"{location}: {detail}"
    logger.warning("Request validation failed. traceId=%s path=%s detail=%s", _trace_id(request), request.url.path, message)
    return _error_json_response(request, PARAM_ERROR, message)


@app.exception_handler(Exception)
async def handle_unexpected_exception(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("Unhandled exception. traceId=%s path=%s", _trace_id(request), request.url.path)
    return _error_json_response(request, INTERNAL_ERROR, "服务内部错误，请稍后重试")


@app.on_event("startup")
async def startup_event() -> None:
    logger.info("Starting %s", settings.PROJECT_NAME)
    logger.info("Vector store will initialize lazily when first used.")


@app.get("/", response_model=BaseResponse, tags=["system"])
def read_root() -> BaseResponse:
    return success_response({"service": settings.PROJECT_NAME})


@app.get("/python/v1/health", response_model=BaseResponse, tags=["system"])
def health() -> BaseResponse:
    return success_response({"service": settings.PROJECT_NAME, "status": "UP"})


def _error_json_response(request: Request, code: int, message: str, status_code: int = 200) -> JSONResponse:
    payload = error_response(code, message).model_dump()
    response = JSONResponse(status_code=status_code, content=payload)
    response.headers["X-Trace-Id"] = _trace_id(request)
    return response


def _trace_id(request: Request) -> str:
    return getattr(request.state, "trace_id", "N/A")
