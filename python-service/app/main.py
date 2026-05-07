from __future__ import annotations

import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1 import router as api_v1_router
from app.core.config import settings
from app.schemas.parse import BaseResponse, success_response

logger = logging.getLogger(__name__)

app = FastAPI(title=settings.PROJECT_NAME)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_v1_router, prefix="/python/v1")


@app.on_event("startup")
async def startup_event() -> None:
    logger.info("Starting %s", settings.PROJECT_NAME)
    try:
        from app.services.vector_store import get_vector_store

        get_vector_store()
        logger.info("Vector store dependency is ready.")
    except Exception as exc:  # noqa: BLE001
        logger.warning("Vector store is not ready; continuing without it: %s", exc)


@app.get("/", response_model=BaseResponse, tags=["system"])
def read_root() -> BaseResponse:
    return success_response({"service": settings.PROJECT_NAME})


@app.get("/python/v1/health", response_model=BaseResponse, tags=["system"])
def health() -> BaseResponse:
    return success_response({"service": settings.PROJECT_NAME, "status": "UP"})
