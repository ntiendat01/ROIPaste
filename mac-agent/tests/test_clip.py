"""Clipboard route tests using the in-memory adapter."""

from __future__ import annotations

from uuid import uuid4

from fastapi.testclient import TestClient
from tests.conftest import auth_headers
from tests.fakes import FakeClipboardAdapter

from app.models.clip import MAX_TEXT_BYTES

UNICODE_TEXT = "Xin chào từ ROIPaste 👋\nDòng thứ hai"


def _body(text: str, message_id: str | None = None) -> dict[str, str]:
    return {
        "message_id": message_id or str(uuid4()),
        "origin": "android",
        "kind": "text",
        "text": text,
    }


def test_empty_clipboard_returns_204(client: TestClient, token: str) -> None:
    response = client.get("/v1/clip", headers=auth_headers(token))
    assert response.status_code == 204
    assert response.headers.get("cache-control") == "no-store"
    assert response.content == b""


def test_unicode_multiline_round_trip(
    client: TestClient, clipboard: FakeClipboardAdapter, token: str
) -> None:
    message_id = str(uuid4())
    post = client.post(
        "/v1/clip",
        json=_body(UNICODE_TEXT, message_id),
        headers=auth_headers(token),
    )
    assert post.status_code == 200
    assert post.json() == {
        "accepted": True,
        "message_id": message_id,
        "result": "clipboard_updated",
    }
    assert clipboard.write_count == 1
    assert clipboard._text == UNICODE_TEXT

    fetched = client.get("/v1/clip", headers=auth_headers(token))
    assert fetched.status_code == 200
    payload = fetched.json()
    assert payload["origin"] == "mac"
    assert payload["kind"] == "text"
    assert payload["text"] == UNICODE_TEXT
    assert payload["message_id"] != message_id
    assert fetched.headers.get("cache-control") == "no-store"


def test_exactly_256_kib_accepted(client: TestClient, token: str) -> None:
    text = "a" * MAX_TEXT_BYTES
    response = client.post("/v1/clip", json=_body(text), headers=auth_headers(token))
    assert response.status_code == 200
    assert response.json()["result"] == "clipboard_updated"


def test_one_byte_over_limit_is_413(
    client: TestClient, clipboard: FakeClipboardAdapter, token: str
) -> None:
    text = "a" * (MAX_TEXT_BYTES + 1)
    response = client.post("/v1/clip", json=_body(text), headers=auth_headers(token))
    assert response.status_code == 413
    assert response.json() == {
        "error": {"code": "PAYLOAD_TOO_LARGE", "message": "Text exceeds the 256 KiB limit"}
    }
    assert clipboard.write_count == 0


def test_invalid_json_is_400(client: TestClient, token: str) -> None:
    response = client.post(
        "/v1/clip",
        content=b"{not-json",
        headers={**auth_headers(token), "Content-Type": "application/json"},
    )
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_REQUEST"


def test_bad_uuid_is_400(client: TestClient, token: str) -> None:
    body = _body("hello", message_id="not-a-uuid")
    response = client.post("/v1/clip", json=body, headers=auth_headers(token))
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_REQUEST"


def test_wrong_kind_is_400(client: TestClient, token: str) -> None:
    body = _body("hello")
    body["kind"] = "image"
    response = client.post("/v1/clip", json=body, headers=auth_headers(token))
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_REQUEST"


def test_wrong_origin_is_400(client: TestClient, token: str) -> None:
    body = _body("hello")
    body["origin"] = "windows"
    response = client.post("/v1/clip", json=body, headers=auth_headers(token))
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "INVALID_REQUEST"


def test_duplicate_id_writes_once(
    client: TestClient, clipboard: FakeClipboardAdapter, token: str
) -> None:
    message_id = str(uuid4())
    first = client.post("/v1/clip", json=_body("first", message_id), headers=auth_headers(token))
    second = client.post("/v1/clip", json=_body("second", message_id), headers=auth_headers(token))
    assert first.status_code == 200
    assert first.json()["result"] == "clipboard_updated"
    assert second.status_code == 200
    assert second.json() == {
        "accepted": True,
        "message_id": message_id,
        "result": "duplicate",
    }
    assert clipboard.write_count == 1
    assert clipboard._text == "first"


def test_clipboard_error_is_503_without_success(
    client: TestClient, clipboard: FakeClipboardAdapter, token: str
) -> None:
    clipboard.fail = True
    response = client.post("/v1/clip", json=_body("secret-text"), headers=auth_headers(token))
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "CLIPBOARD_UNAVAILABLE"
    assert "accepted" not in response.json()
    assert clipboard.write_count == 0
