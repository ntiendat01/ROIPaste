"""End-to-end HTTPS tests against a real uvicorn TLS server."""

from __future__ import annotations

import socket
import threading
import time
from pathlib import Path

import httpx
import uvicorn
from tests.fakes import FakeClipboardAdapter

from app.config import Settings
from app.main import create_app
from app.security.auth import TokenStore
from app.security.certs import CertificateStore
from app.security.devices import DeviceStore, FileDeviceBackend


def _free_port() -> int:
    probe = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    probe.bind(("127.0.0.1", 0))
    port = probe.getsockname()[1]
    probe.close()
    return port


def _start(app: object, host: str, port: int, cert: Path, key: Path) -> uvicorn.Server:
    config = uvicorn.Config(
        app,
        host=host,
        port=port,
        ssl_certfile=str(cert),
        ssl_keyfile=str(key),
        log_level="warning",
        access_log=False,
    )
    server = uvicorn.Server(config)
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    for _ in range(80):
        if server.started:
            return server
        time.sleep(0.05)
    raise RuntimeError("uvicorn TLS server did not start")


def test_https_with_matching_certificate_and_rejects_wrong_pin(tmp_path: Path) -> None:
    store = CertificateStore(tmp_path / "server")
    store.generate("127.0.0.1")
    other = CertificateStore(tmp_path / "other")
    other.generate("127.0.0.1")
    assert store.pin() != other.pin()

    devices = DeviceStore(FileDeviceBackend(tmp_path / "paired-devices.json"))
    devices.add("ace5", "Ace 5", "lan-credential")
    settings = Settings(
        host="127.0.0.1",
        port=5000,
        profile="lan",
        tls_enabled=True,
        config_dir=tmp_path,
    )
    app = create_app(
        settings=settings,
        clipboard=FakeClipboardAdapter(),
        token_store=TokenStore("usb-session"),
        devices=devices,
    )
    port = _free_port()
    server = _start(app, "127.0.0.1", port, store.cert_path, store.key_path)
    url = f"https://127.0.0.1:{port}"
    try:
        with httpx.Client(verify=str(store.cert_path), timeout=3.0) as client:
            health = client.get(f"{url}/v1/health")
            assert health.status_code == 200
            assert health.json()["protocol"] == 1
            denied = client.get(f"{url}/v1/clip")
            assert denied.status_code == 401
            ok = client.get(
                f"{url}/v1/status",
                headers={"Authorization": "Bearer lan-credential"},
            )
            assert ok.status_code == 200
            assert ok.json()["transport"] == "lan"
        with httpx.Client(verify=str(other.cert_path), timeout=3.0) as client:
            try:
                client.get(f"{url}/v1/health")
                raised = False
            except httpx.TransportError:
                raised = True
        assert raised, "A mismatched certificate must fail closed"
        # Plain HTTP to the TLS port must not succeed.
        with httpx.Client(timeout=2.0) as client:
            try:
                client.get(f"http://127.0.0.1:{port}/v1/health")
                http_ok = True
            except httpx.TransportError:
                http_ok = False
        assert http_ok is False
    finally:
        server.should_exit = True
