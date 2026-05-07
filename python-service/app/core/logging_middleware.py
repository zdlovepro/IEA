from __future__ import annotations

import time
import uuid

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import Response

from app.utils.logger import logger


class RequestLoggingMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next) -> Response:
        trace_id = request.headers.get("X-Trace-Id") or uuid.uuid4().hex
        request.state.trace_id = trace_id
        start = time.perf_counter()

        try:
            response = await call_next(request)
        except Exception:
            duration_ms = int((time.perf_counter() - start) * 1000)
            logger.exception(
                "traceId=%s method=%s path=%s status=%s durationMs=%s",
                trace_id,
                request.method,
                request.url.path,
                500,
                duration_ms,
            )
            raise

        duration_ms = int((time.perf_counter() - start) * 1000)
        response.headers["X-Trace-Id"] = trace_id
        logger.info(
            "traceId=%s method=%s path=%s status=%s durationMs=%s",
            trace_id,
            request.method,
            request.url.path,
            response.status_code,
            duration_ms,
        )
        return response
