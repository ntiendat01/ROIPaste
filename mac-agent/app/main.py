"""FastAPI application factory for the ROIPaste Mac agent."""

from fastapi import FastAPI

from app.config import Settings


def create_app(settings: Settings | None = None) -> FastAPI:
    """Build the API. Only GET /v1/health is exposed in this milestone."""
    resolved = settings or Settings()
    application = FastAPI(
        title="ROIPaste Mac Agent",
        version="0.1.0",
        summary=f"Development server defaults to {resolved.host}:{resolved.port}",
    )

    @application.get("/v1/health")
    def health() -> dict[str, str | int]:
        return {"status": "ok", "protocol": 1}

    return application


app = create_app()
