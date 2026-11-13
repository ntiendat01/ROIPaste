"""Authenticated session status for USB and LAN profiles."""

from fastapi import APIRouter, Request

from app.models.status import StatusResponse
from app.security.devices import DeviceStore

router = APIRouter()


@router.get("/v1/status", response_model=StatusResponse)
def status(request: Request) -> StatusResponse:
    settings = request.app.state.settings
    devices = getattr(request.app.state, "devices", None)
    if getattr(settings, "profile", "usb") == "lan" and isinstance(devices, DeviceStore):
        return StatusResponse(
            paired=devices.has_active(),
            sync_paused=devices.paused,
            transport="lan",
        )
    return StatusResponse(paired=True, sync_paused=False, transport="usb")
