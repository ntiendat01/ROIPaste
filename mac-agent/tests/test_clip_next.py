"""Long-poll /v1/clip/next auth, timeout, pause, and delivery."""

from __future__ import annotations

import logging
import threading
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from tests.conftest import auth_headers
from tests.fakes import FakeClipboardAdapter

from app.config import Settings
from app.core.inbox import ClipInbox
from app.main import create_app
from app.models.clip import ClipKind, ClipPayload, Origin
from app.security.auth import TokenStore

TOKEN = "clip-next-token"


def _app(clipboard: FakeClipboardAdapter | None = None, *, watch: bool = False) -> TestClient:
    settings = Settings(watcher_enabled=watch)
    application = create_app(
        settings=settings,
        clipboard=clipboard or FakeClipboardAdapter(),
        token_store=TokenStore(TOKEN),
    )
    return TestClient(application)


def test_clip_next_requires_auth() -> None:
    with _app() as client:
        missing = client.get("/v1/clip/next?timeout=0")
        assert missing.status_code == 401
        wrong = client.get("/v1/clip/next?timeout=0", headers=auth_headers("nope"))
        assert wrong.status_code == 401


def test_clip_next_timeout_returns_204() -> None:
    with _app() as client:
        response = client.get("/v1/clip/next?timeout=0.2", headers=auth_headers(TOKEN))
        assert response.status_code == 204


def test_clip_next_returns_published_item() -> None:
    with _app() as client:
        inbox: ClipInbox = client.app.state.inbox
        payload = ClipPayload(
            message_id=uuid4(),
            origin=Origin.mac,
            kind=ClipKind.text,
            text="hello from watcher",
        )
        item = inbox.publish(payload)
        response = client.get("/v1/clip/next?since=0&timeout=0", headers=auth_headers(TOKEN))
        assert response.status_code == 200
        body = response.json()
        assert body["text"] == "hello from watcher"
        assert body["sequence"] == item.sequence
        assert body["origin"] == "mac"
        assert body["byte_count"] == len("hello from watcher")
        empty = client.get(
            f"/v1/clip/next?since={item.sequence}&timeout=0",
            headers=auth_headers(TOKEN),
        )
        assert empty.status_code == 204


def test_clip_next_pause_returns_403() -> None:
    with _app() as client:
        client.app.state.watcher.set_paused(True)
        response = client.get("/v1/clip/next?timeout=0", headers=auth_headers(TOKEN))
        assert response.status_code == 403
        assert response.json()["error"]["code"] == "SYNC_PAUSED"


def test_watcher_toggle_api() -> None:
    with _app() as client:
        before = client.get("/v1/watcher", headers=auth_headers(TOKEN))
        assert before.status_code == 200
        assert before.json()["enabled"] is False
        enabled = client.post("/v1/watcher", json={"enabled": True}, headers=auth_headers(TOKEN))
        assert enabled.status_code == 200
        assert enabled.json()["enabled"] is True
        status = client.get("/v1/status", headers=auth_headers(TOKEN))
        assert status.json()["watcher_enabled"] is True
        client.post("/v1/watcher", json={"enabled": False}, headers=auth_headers(TOKEN))


def test_long_poll_delivers_when_item_arrives_during_wait() -> None:
    with _app() as client:
        inbox: ClipInbox = client.app.state.inbox
        holder: list[int] = []

        def wait() -> None:
            response = client.get("/v1/clip/next?since=0&timeout=2", headers=auth_headers(TOKEN))
            holder.append(response.status_code)
            if response.status_code == 200:
                holder.append(response.json()["sequence"])

        thread = threading.Thread(target=wait)
        thread.start()
        # Give the waiter a moment to attach.
        for _ in range(50):
            if inbox.waiter_count:
                break
            threading.Event().wait(0.02)
        inbox.publish(
            ClipPayload(message_id=uuid4(), origin=Origin.mac, kind=ClipKind.text, text="late")
        )
        thread.join(timeout=3)
        assert not thread.is_alive()
        assert holder and holder[0] == 200


def test_watcher_does_not_log_clipboard_text(caplog: pytest.LogCaptureFixture) -> None:
    clipboard = FakeClipboardAdapter("visible-at-start")
    secret = "watcher-secret-text-zzz"
    with _app(clipboard, watch=True) as client:
        caplog.set_level(logging.DEBUG)
        client.app.state.watcher.poll_once()
        clipboard.write_text(secret)
        client.app.state.watcher.poll_once()
        combined = caplog.text
        assert secret not in combined
        assert "event=watcher_published" in combined
        assert TOKEN not in combined
