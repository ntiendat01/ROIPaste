"""Health endpoint tests for the Mac agent scaffold."""

from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


def test_health_ok() -> None:
    client = TestClient(create_app())
    response = client.get("/v1/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok", "protocol": 1}


def test_default_bind_settings() -> None:
    settings = Settings()
    assert settings.host == "127.0.0.1"
    assert settings.port == 5000
    assert settings == Settings(host="127.0.0.1", port=5000)
