"""FastAPI application factory for the ROIPaste Mac agent."""

from fastapi import Depends, FastAPI

from app.api.clip import router as clip_router
from app.api.devices import router as devices_router
from app.api.handlers import register_exception_handlers
from app.api.health import router as health_router
from app.api.pair import router as pair_router
from app.api.status import router as status_router
from app.config import Settings
from app.core.clipboard import ClipboardAdapter, MacClipboardAdapter
from app.core.transfer import TransferService
from app.security.auth import TokenStore, require_auth
from app.security.devices import DeviceStore
from app.security.pairing import PairingService
from app.security.ratelimit import RateLimiter


def create_app(
    settings: Settings | None = None,
    clipboard: ClipboardAdapter | None = None,
    token_store: TokenStore | None = None,
    devices: DeviceStore | None = None,
    pairing: PairingService | None = None,
    pairing_limiter: RateLimiter | None = None,
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
        summary=f"Clipboard companion ({resolved.profile} {resolved.host}:{resolved.port})",
    )
    application.state.settings = resolved
    application.state.token_store = tokens
    application.state.transfer = TransferService(adapter)
    application.state.devices = devices
    application.state.pairing = pairing
    application.state.pairing_limiter = pairing_limiter
    register_exception_handlers(application)
    application.include_router(health_router)
    application.include_router(pair_router)
    application.include_router(status_router, dependencies=[Depends(require_auth)])
    application.include_router(clip_router, dependencies=[Depends(require_auth)])
    application.include_router(devices_router, dependencies=[Depends(require_auth)])
    return application


app = create_app()
