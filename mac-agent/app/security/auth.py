"""Per-session and per-device bearer verification."""

from __future__ import annotations

import hmac
from typing import Annotated

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.errors import ForbiddenError, UnauthenticatedError
from app.core.logging import log_event
from app.security.devices import DeviceStore

_bearer_scheme = HTTPBearer(auto_error=False)


def generate_session_token() -> str:
    import secrets

    return secrets.token_urlsafe(32)


class TokenStore:
    """Holds the current process session token. Not loaded from the environment."""

    def __init__(self, token: str | None = None) -> None:
        self._token = token if token is not None else generate_session_token()

    @property
    def token(self) -> str:
        return self._token

    def verify(self, provided: str) -> bool:
        return hmac.compare_digest(provided, self._token)


def get_token_store(request: Request) -> TokenStore:
    store = getattr(request.app.state, "token_store", None)
    if not isinstance(store, TokenStore):
        raise RuntimeError("Token store is not configured")
    return store


def get_device_store(request: Request) -> DeviceStore | None:
    store = getattr(request.app.state, "devices", None)
    if isinstance(store, DeviceStore):
        return store
    return None


def _profile(request: Request) -> str:
    settings = getattr(request.app.state, "settings", None)
    return getattr(settings, "profile", "usb") if settings is not None else "usb"


async def require_auth(
    request: Request,
    credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(_bearer_scheme)
    ],
) -> None:
    """Require a valid Authorization: Bearer token before clipboard access."""
    if (
        credentials is None
        or credentials.scheme.lower() != "bearer"
        or not credentials.credentials
    ):
        log_event("unauthenticated")
        raise UnauthenticatedError()
    token = credentials.credentials
    profile = _profile(request)
    session = get_token_store(request)
    if session.verify(token):
        if profile == "lan":
            log_event("unauthenticated")
            raise UnauthenticatedError()
        return
    devices = get_device_store(request)
    record = devices.find_by_credential(token) if devices is not None else None
    if record is None:
        log_event("unauthenticated")
        raise UnauthenticatedError()
    if record.revoked:
        log_event("revoked")
        raise ForbiddenError("REVOKED", "This device has been revoked")
    if devices is not None and devices.paused:
        log_event("sync_paused")
        raise ForbiddenError("SYNC_PAUSED", "Sync is paused on the Mac")
    request.state.device_id = record.device_id
