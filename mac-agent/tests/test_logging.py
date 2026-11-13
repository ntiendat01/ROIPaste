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


def test_pairing_completion_does_not_log_credential(tmp_path, caplog) -> None:
    import logging

    from fastapi.testclient import TestClient
    from tests.fakes import FakeClipboardAdapter

    from app.config import Settings
    from app.main import create_app
    from app.security.auth import TokenStore
    from app.security.devices import DeviceStore, FileDeviceBackend
    from app.security.pairing import PairingService

    devices = DeviceStore(FileDeviceBackend(tmp_path / "devices.json"))
    service = PairingService(
        host="192.168.1.20",
        port=5000,
        pin="sha256/AAAA",
        devices=devices,
        clock=lambda: 1000.0,
        confirm=lambda _a, _f: True,
    )
    enrollment = service.open_window()
    app = create_app(
        settings=Settings(host="127.0.0.1", profile="lan", tls_enabled=True, config_dir=tmp_path),
        clipboard=FakeClipboardAdapter(),
        token_store=TokenStore("usb-session"),
        devices=devices,
        pairing=service,
    )
    caplog.set_level(logging.DEBUG)
    client = TestClient(app)
    response = client.post(
        "/v1/pair/complete",
        json={"enrollment_token": enrollment.token, "device_id": "ace5", "alias": "Ace 5"},
    )
    assert response.status_code == 201
    credential = response.json()["credential"]
    combined = "\n".join(record.getMessage() for record in caplog.records)
    assert credential not in combined
    assert enrollment.token not in combined
