"""Pairing routes. These do not use the clipboard bearer; enrollment is one-time."""

from __future__ import annotations

import sys

from fastapi import APIRouter, Request

from app.core.errors import PairingError
from app.models.pairing import PairCompleteRequest, PairCompleteResponse, PairStartResponse
from app.security.pairing import PairingService
from app.security.ratelimit import RateLimiter

router = APIRouter()


def _pairing(request: Request) -> PairingService:
    service = getattr(request.app.state, "pairing", None)
    if not isinstance(service, PairingService):
        raise PairingError("PAIRING_WINDOW_CLOSED", "Pairing is not configured", 403)
    return service


def _limiter(request: Request) -> RateLimiter | None:
    limiter = getattr(request.app.state, "pairing_limiter", None)
    return limiter if isinstance(limiter, RateLimiter) else None


def _client_key(request: Request, action: str) -> str:
    client = request.client.host if request.client is not None else "unknown"
    return f"{action}:{client}"


@router.post("/v1/pair/start", response_model=PairStartResponse)
def pair_start(request: Request) -> PairStartResponse:
    limiter = _limiter(request)
    if limiter is not None:
        limiter.check(_client_key(request, "start"))
    payload = _pairing(request).start()
    expires_at = payload["expires_at"]
    qr = payload["qr"]
    if not isinstance(expires_at, int) or not isinstance(qr, dict):
        raise PairingError("INVALID_REQUEST", "Invalid pairing payload", 500)
    return PairStartResponse(expires_at=expires_at, qr=qr)


@router.post("/v1/pair/complete", response_model=PairCompleteResponse, status_code=201)
def pair_complete(request: Request, body: PairCompleteRequest) -> PairCompleteResponse:
    limiter = _limiter(request)
    if limiter is not None:
        limiter.check(_client_key(request, "complete"))
    result = _pairing(request).complete(body.enrollment_token, body.device_id, body.alias)
    print("Pairing complete. The phone can now connect over Wi-Fi.", file=sys.stdout, flush=True)
    return PairCompleteResponse(
        device_id=result.device_id,
        alias=result.alias,
        credential=result.credential,
        transport="lan",
    )
