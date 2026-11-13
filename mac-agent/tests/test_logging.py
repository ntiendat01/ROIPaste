"""Logs must never contain clipboard text or the session token."""

from __future__ import annotations

import logging
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from tests.conftest import TEST_TOKEN, auth_headers

SECRET_TEXT = "unique-clipboard-secret-xyz"


def test_clip_text_and_token_are_absent_from_logs(
    client: TestClient, token: str, caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level(logging.DEBUG)
    message_id = str(uuid4())
    response = client.post(
        "/v1/clip",
        json={
            "message_id": message_id,
            "origin": "android",
            "kind": "text",
            "text": SECRET_TEXT,
        },
        headers=auth_headers(token),
    )
    assert response.status_code == 200
    combined = caplog.text
    assert SECRET_TEXT not in combined
    assert token not in combined
    assert TEST_TOKEN not in combined
    assert "event=clip_write" in combined
    assert "bytes=" in combined
    prefix = message_id.split("-", 1)[0]
    assert f"id={prefix}" in combined


def test_auth_failure_does_not_log_provided_token(
    client: TestClient, caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level(logging.DEBUG)
    stolen = "forged-bearer-token-value"
    response = client.get("/v1/clip", headers=auth_headers(stolen))
    assert response.status_code == 401
    assert stolen not in caplog.text
    assert TEST_TOKEN not in caplog.text
    assert "event=unauthenticated" in caplog.text
