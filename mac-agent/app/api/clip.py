"""Authenticated clipboard routes."""

from typing import Annotated

from fastapi import APIRouter, Depends, Response
from fastapi.responses import JSONResponse

from app.api.deps import get_transfer_service
from app.core.transfer import TransferService
from app.models.clip import ClipAccepted, ClipRequest

router = APIRouter()
_NO_STORE = {"Cache-Control": "no-store"}
TransferDep = Annotated[TransferService, Depends(get_transfer_service)]


@router.post("/v1/clip", response_model=ClipAccepted)
def post_clip(body: ClipRequest, service: TransferDep) -> ClipAccepted:
    return service.put_clip(body)


@router.get("/v1/clip")
def get_clip(service: TransferDep) -> Response:
    payload = service.get_clip()
    if payload is None:
        return Response(status_code=204, headers=_NO_STORE)
    return JSONResponse(
        status_code=200,
        content=payload.model_dump(mode="json"),
        headers=_NO_STORE,
    )
