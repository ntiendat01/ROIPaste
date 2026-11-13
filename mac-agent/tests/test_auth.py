"""Authentication denial tests for clipboard and status routes."""

import pytest
from fastapi.testclient import TestClient
from tests.conftest import auth_headers

CLIP_BODY = {
    "message_id": "4cf0408d-8393-41a0-8706-53fa9754315f",
    "origin": "android",
    "kind": "text",
    "text": "hello",
}


@pytest.mark.parametrize("method,path", [("GET", "/v1/clip"), ("GET", "/v1/status")])
def test_missing_token_on_get(client: TestClient, method: str, path: str) -> None:
    response = client.request(method, path)
    assert response.status_code == 401
    body = response.json()
    assert body["error"]["code"] == "UNAUTHENTICATED"
    assert "error" in body and "code" in body["error"]
    assert response.headers.get("www-authenticate", "").lower().startswith("bearer")


def test_missing_token_on_post_clip(client: TestClient) -> None:
    response = client.post("/v1/clip", json=CLIP_BODY)
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "UNAUTHENTICATED"


@pytest.mark.parametrize("method,path", [("GET", "/v1/clip"), ("GET", "/v1/status")])
def test_wrong_token_on_get(client: TestClient, method: str, path: str) -> None:
    response = client.request(method, path, headers=auth_headers("wrong-token"))
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "UNAUTHENTICATED"


def test_wrong_token_on_post_clip(client: TestClient) -> None:
    response = client.post("/v1/clip", json=CLIP_BODY, headers=auth_headers("wrong-token"))
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "UNAUTHENTICATED"


def test_status_ok_with_valid_token(client: TestClient, token: str) -> None:
    response = client.get("/v1/status", headers=auth_headers(token))
    assert response.status_code == 200
    assert response.json() == {"paired": True, "sync_paused": False, "transport": "usb"}
