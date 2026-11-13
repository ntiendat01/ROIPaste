"""Authenticated session status for USB and LAN profiles."""

from fastapi import APIRouter, Request

from app.core.watcher import ClipboardWatcher
from app.models.status import StatusResponse
from app.security.devices import DeviceStore

router = APIRouter()


@router.get("/v1/status", response_model=StatusResponse)
def status(request: Request) -> StatusResponse:
    settings = request.app.state.settings
    devices = getattr(request.app.state, "devices", None)
    watcher = getattr(request.app.state, "watcher", None)
    watcher_enabled = isinstance(watcher, ClipboardWatcher) and watcher.enabled
    paused = isinstance(watcher, ClipboardWatcher) and watcher.paused
    if getattr(settings, "profile", "usb") == "lan" and isinstance(devices, DeviceStore):
        return StatusResponse(
            paired=devices.has_active(),
            sync_paused=paused or devices.paused,
            transport="lan",
            watcher_enabled=watcher_enabled,
        )
    return StatusResponse(
        paired=True,
        sync_paused=paused,
        transport="usb",
        watcher_enabled=watcher_enabled,
    )
