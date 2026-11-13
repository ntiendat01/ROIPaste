"""Authenticated device revocation."""

from __future__ import annotations

from fastapi import APIRouter, Request

from app.core.errors import ForbiddenError, UnauthenticatedError
from app.security.devices import DeviceStore

router = APIRouter()


@router.delete("/v1/devices/{device_id}", status_code=204)
def revoke_device(device_id: str, request: Request) -> None:
    store = getattr(request.app.state, "devices", None)
    if not isinstance(store, DeviceStore):
        raise UnauthenticatedError()
    caller = getattr(request.state, "device_id", None)
    if caller is None or caller != device_id:
        raise ForbiddenError("FORBIDDEN", "A device may only revoke itself")
    if store.revoke(device_id) is None:
        raise ForbiddenError("FORBIDDEN", "Device is not registered")
