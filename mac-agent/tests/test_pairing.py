"""Pairing window, expiry, single use, confirmation, and QR payload tests."""

from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient
from tests.fakes import FakeClipboardAdapter

from app.config import Settings
from app.main import create_app
from app.security.auth import TokenStore
from app.security.devices import DeviceStore, FileDeviceBackend, hash_credential
from app.security.pairing import PairingService
from app.security.ratelimit import RateLimiter


class Clock:
    def __init__(self, now: float = 1_000.0) -> None:
        self.now = now

    def __call__(self) -> float:
        return self.now


def _service(
    tmp_path: Path,
    clock: Clock,
    confirm: bool = True,
) -> tuple[PairingService, DeviceStore]:
    devices = DeviceStore(FileDeviceBackend(tmp_path / "paired-devices.json"))
    service = PairingService(
        host="192.168.1.20",
        port=5000,
        pin="sha256/ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefg=",
        devices=devices,
        clock=clock,
        confirm=lambda _alias, _fp: confirm,
    )
    return service, devices


def test_start_rejected_when_window_closed(tmp_path: Path) -> None:
    clock = Clock()
    service, _devices = _service(tmp_path, clock)
    with pytest.raises(Exception, match="Open a pairing window|not open"):
        service.start()


def test_qr_payload_has_no_permanent_secret(tmp_path: Path) -> None:
    clock = Clock()
    service, _devices = _service(tmp_path, clock)
    enrollment = service.open_window()
    payload = service.qr_payload(enrollment)
    assert payload["v"] == 1
    assert payload["host"] == "192.168.1.20"
    assert payload["port"] == 5000
    assert payload["pin"].startswith("sha256/")
    assert payload["enrollment"] == enrollment.token
    assert "credential" not in payload
    assert "token" not in payload
    raw = service.qr_json(enrollment)
    assert "credential" not in raw


def test_enrollment_expires(tmp_path: Path) -> None:
    clock = Clock(1_000.0)
    service, _devices = _service(tmp_path, clock)
    enrollment = service.open_window()
    clock.now = 1_000.0 + 121
    with pytest.raises(Exception, match="expired|EXPIRED"):
        service.complete(enrollment.token, "device-1", "Ace 5")


def test_enrollment_is_single_use(tmp_path: Path) -> None:
    clock = Clock()
    service, devices = _service(tmp_path, clock)
    enrollment = service.open_window()
    first = service.complete(enrollment.token, "device-1", "Ace 5")
    assert devices.get("device-1") is not None
    assert devices.get("device-1").credential_sha256 == hash_credential(first.credential)
    with pytest.raises(Exception, match="used|USED|not open"):
        service.complete(enrollment.token, "device-2", "Other")


def test_rejected_without_confirmation(tmp_path: Path) -> None:
    clock = Clock()
    service, devices = _service(tmp_path, clock, confirm=False)
    enrollment = service.open_window()
    with pytest.raises(Exception, match="not confirmed"):
        service.complete(enrollment.token, "device-1", "Ace 5")
    assert devices.get("device-1") is None
    with pytest.raises(Exception, match="used|USED|not open"):
        service.complete(enrollment.token, "device-1", "Ace 5")


def _lan_client(
    tmp_path: Path, clock: Clock, confirm: bool = True
) -> tuple[TestClient, PairingService]:
    service, devices = _service(tmp_path, clock, confirm=confirm)
    settings = Settings(host="127.0.0.1", profile="lan", tls_enabled=True, config_dir=tmp_path)
    app = create_app(
        settings=settings,
        clipboard=FakeClipboardAdapter(),
        token_store=TokenStore("usb-session"),
        devices=devices,
        pairing=service,
        pairing_limiter=RateLimiter(limit=5, window_seconds=60, clock=clock),
    )
    return TestClient(app), service


def test_http_start_and_complete_roundtrip(tmp_path: Path) -> None:
    clock = Clock()
    client, service = _lan_client(tmp_path, clock)
    service.open_window()
    start = client.post("/v1/pair/start")
    assert start.status_code == 200
    enrollment = start.json()["qr"]["enrollment"]
    assert "credential" not in start.json()["qr"]
    complete = client.post(
        "/v1/pair/complete",
        json={"enrollment_token": enrollment, "device_id": "ace5", "alias": "Ace 5"},
    )
    assert complete.status_code == 201
    body = complete.json()
    assert body["device_id"] == "ace5"
    assert body["credential"]
    # USB session token is rejected on LAN.
    denied = client.get("/v1/status", headers={"Authorization": "Bearer usb-session"})
    assert denied.status_code == 401
    ok = client.get("/v1/status", headers={"Authorization": f"Bearer {body['credential']}"})
    assert ok.status_code == 200
    assert ok.json()["transport"] == "lan"
    assert ok.json()["paired"] is True


def test_rate_limit_pairing_start(tmp_path: Path) -> None:
    clock = Clock()
    client, service = _lan_client(tmp_path, clock)
    service.open_window()
    for _ in range(5):
        assert client.post("/v1/pair/start").status_code == 200
    limited = client.post("/v1/pair/start")
    assert limited.status_code == 429
    assert limited.json()["error"]["code"] == "RATE_LIMITED"
