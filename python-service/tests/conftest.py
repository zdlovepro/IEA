from __future__ import annotations

import asyncio
import sys
from pathlib import Path

import httpx
import pytest

PROJECT_ROOT = Path(__file__).resolve().parents[1]
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

from app.main import app  # noqa: E402
from app.services import vector_store as vector_store_module  # noqa: E402


class _StartupVectorStoreStub:
    backend_name = "test_stub"


@pytest.fixture
def request_app(monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setattr(vector_store_module, "get_vector_store", lambda: _StartupVectorStoreStub())

    def _request(method: str, path: str, **kwargs):
        async def _run():
            transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
            async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
                return await client.request(method, path, **kwargs)

        return asyncio.run(_run())

    return _request
