"""Authenticated session status for the USB development profile."""

from fastapi import APIRouter

from app.models.status import StatusResponse

router = APIRouter()


@router.get("/v1/status", response_model=StatusResponse)
def status() -> StatusResponse:
    return StatusResponse(paired=True, sync_paused=False, transport="usb")
