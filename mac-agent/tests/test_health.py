"""Health endpoint tests. The route must stay unauthenticated and clip-free."""

from fastapi.testclient import TestClient
from tests.conftest import auth_headers
from tests.fakes import FakeClipboardAdapter

from app.config import Settings
from app.main import create_app


def test_health_ok() -> None:
    client = TestClient(create_app())
    response = client.get("/v1/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "protocol": 1}


def test_health_does_not_require_auth() -> None:
    client = TestClient(create_app())
    response = client.get("/v1/health")
    assert response.status_code == 200
    assert "Authorization" not in response.request.headers


def test_health_does_not_read_clipboard() -> None:
    clipboard = FakeClipboardAdapter(text="should-not-be-read")
    client = TestClient(create_app(clipboard=clipboard))
    response = client.get("/v1/health")
    assert response.status_code == 200
    assert clipboard.read_count == 0
    assert "should-not-be-read" not in response.text


def test_health_with_token_still_has_no_clip_data(client: TestClient, token: str) -> None:
    response = client.get("/v1/health", headers=auth_headers(token))
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "protocol": 1}
    assert "text" not in response.json()


def test_default_bind_settings() -> None:
    settings = Settings()
    assert settings.host == "127.0.0.1"
    assert settings.port == 5000
    assert settings == Settings(host="127.0.0.1", port=5000)
