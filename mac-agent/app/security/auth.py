"""Per-session bearer token generation and constant-time verification."""

from __future__ import annotations

import hmac
import secrets
from typing import Annotated

from fastapi import Depends, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.errors import UnauthenticatedError
from app.core.logging import log_event

_bearer_scheme = HTTPBearer(auto_error=False)


def generate_session_token() -> str:
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


async def require_auth(
    request: Request,
    credentials: Annotated[
        HTTPAuthorizationCredentials | None, Depends(_bearer_scheme)
    ],
) -> None:
    """Require a valid Authorization: Bearer token before clipboard access."""
    store = get_token_store(request)
    if (
        credentials is None
        or credentials.scheme.lower() != "bearer"
        or not store.verify(credentials.credentials)
    ):
        log_event("unauthenticated")
        raise UnauthenticatedError()
