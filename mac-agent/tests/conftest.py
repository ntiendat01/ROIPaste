"""Shared fixtures for Mac agent tests."""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient
from tests.fakes import FakeClipboardAdapter

from app.main import create_app
from app.security.auth import TokenStore

TEST_TOKEN = "unit-test-session-token"


@pytest.fixture
def clipboard() -> FakeClipboardAdapter:
    return FakeClipboardAdapter()


@pytest.fixture
def token() -> str:
    return TEST_TOKEN


@pytest.fixture
def client(clipboard: FakeClipboardAdapter, token: str) -> Iterator[TestClient]:
    application = create_app(clipboard=clipboard, token_store=TokenStore(token))
    with TestClient(application) as test_client:
        yield test_client


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}
