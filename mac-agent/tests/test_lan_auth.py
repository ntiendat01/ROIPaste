"""LAN device credential, revoke, and pause tests."""

from pathlib import Path

from fastapi.testclient import TestClient
from tests.fakes import FakeClipboardAdapter

from app.config import Settings
from app.main import create_app
from app.security.auth import TokenStore
from app.security.devices import DeviceStore, FileDeviceBackend

CLIP = {
    "message_id": "4cf0408d-8393-41a0-8706-53fa9754315f",
    "origin": "android",
    "kind": "text",
    "text": "hello",
}
AUTH = {"Authorization": "Bearer good-credential"}


def _client(tmp_path: Path) -> tuple[TestClient, DeviceStore]:
    devices = DeviceStore(FileDeviceBackend(tmp_path / "paired-devices.json"))
    devices.add("ace5", "Ace 5", "good-credential")
    settings = Settings(host="127.0.0.1", profile="lan", tls_enabled=True, config_dir=tmp_path)
    app = create_app(
        settings=settings,
        clipboard=FakeClipboardAdapter(),
        token_store=TokenStore("usb-session"),
        devices=devices,
    )
    return TestClient(app), devices


def test_unknown_token_is_401(tmp_path: Path) -> None:
    client, _devices = _client(tmp_path)
    response = client.get("/v1/clip", headers={"Authorization": "Bearer unknown"})
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "UNAUTHENTICATED"


def test_revoked_credential_is_403(tmp_path: Path) -> None:
    client, devices = _client(tmp_path)
    devices.revoke("ace5")
    response = client.get("/v1/status", headers=AUTH)
    assert response.status_code == 403
    assert response.json()["error"]["code"] == "REVOKED"


def test_paused_sync_is_403(tmp_path: Path) -> None:
    client, devices = _client(tmp_path)
    devices.set_paused(True)
    response = client.post("/v1/clip", json=CLIP, headers=AUTH)
    assert response.status_code == 403
    assert response.json()["error"]["code"] == "SYNC_PAUSED"


def test_self_revoke(tmp_path: Path) -> None:
    client, _devices = _client(tmp_path)
    response = client.delete("/v1/devices/ace5", headers=AUTH)
    assert response.status_code == 204
    again = client.get("/v1/status", headers=AUTH)
    assert again.status_code == 403
    assert again.json()["error"]["code"] == "REVOKED"


def test_cannot_revoke_another_device(tmp_path: Path) -> None:
    client, devices = _client(tmp_path)
    devices.add("other", "Other", "other-cred")
    response = client.delete("/v1/devices/other", headers=AUTH)
    assert response.status_code == 403
