"""Authenticated clipboard routes."""

from typing import Annotated

from fastapi import APIRouter, Depends, Query, Request, Response
from fastapi.responses import JSONResponse

from app.api.deps import get_transfer_service
from app.core.errors import ForbiddenError
from app.core.inbox import ClipInbox
from app.core.transfer import TransferService
from app.core.watcher import ClipboardWatcher
from app.models.clip import ClipAccepted, ClipNextResponse, ClipRequest, utf8_size

router = APIRouter()
_NO_STORE = {"Cache-Control": "no-store"}
TransferDep = Annotated[TransferService, Depends(get_transfer_service)]
DEFAULT_WAIT_SECONDS = 25.0


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


@router.get("/v1/clip/next")
def get_clip_next(
    request: Request,
    since: int = Query(default=0, ge=0),
    timeout: float = Query(default=DEFAULT_WAIT_SECONDS, ge=0.0, le=30.0),
) -> Response:
    watcher = getattr(request.app.state, "watcher", None)
    if isinstance(watcher, ClipboardWatcher) and watcher.paused:
        raise ForbiddenError("SYNC_PAUSED", "Sync is paused on the Mac")
    inbox = request.app.state.inbox
    if not isinstance(inbox, ClipInbox):
        raise RuntimeError("Inbox is not configured")
    item = inbox.wait_next(since, timeout)
    if item is None:
        return Response(status_code=204, headers=_NO_STORE)
    payload = item.payload
    body = ClipNextResponse(
        message_id=payload.message_id,
        origin=payload.origin,
        kind=payload.kind,
        text=payload.text,
        sequence=item.sequence,
        byte_count=utf8_size(payload.text),
    )
    return JSONResponse(
        status_code=200,
        content=body.model_dump(mode="json"),
        headers=_NO_STORE,
    )
