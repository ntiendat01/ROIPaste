"""FastAPI application factory for the ROIPaste Mac agent."""

from fastapi import Depends, FastAPI

from app.api.clip import router as clip_router
from app.api.handlers import register_exception_handlers
from app.api.health import router as health_router
from app.api.status import router as status_router
from app.config import Settings
from app.core.clipboard import ClipboardAdapter, MacClipboardAdapter
from app.core.transfer import TransferService
from app.security.auth import TokenStore, require_auth


def create_app(
    settings: Settings | None = None,
    clipboard: ClipboardAdapter | None = None,
    token_store: TokenStore | None = None,
) -> FastAPI:
    """Build the API. Clipboard routes always require a bearer token."""
    resolved = settings or Settings()
    tokens = token_store or TokenStore()
    adapter: ClipboardAdapter = clipboard if clipboard is not None else MacClipboardAdapter(
        timeout_seconds=resolved.clipboard_timeout_seconds
    )
    application = FastAPI(
        title="ROIPaste Mac Agent",
        version="0.1.0",
        summary=f"Loopback clipboard companion ({resolved.host}:{resolved.port})",
    )
    application.state.settings = resolved
    application.state.token_store = tokens
    application.state.transfer = TransferService(adapter)
    register_exception_handlers(application)
    application.include_router(health_router)
    application.include_router(status_router, dependencies=[Depends(require_auth)])
    application.include_router(clip_router, dependencies=[Depends(require_auth)])
    return application


app = create_app()
