"""Authenticated endpoints to inspect and toggle the clipboard watcher."""

from fastapi import APIRouter, Request

from app.core.watcher import ClipboardWatcher
from app.models.watcher import WatcherState, WatcherUpdate

router = APIRouter()


@router.get("/v1/watcher", response_model=WatcherState)
def get_watcher(request: Request) -> WatcherState:
    return _state(request)


@router.post("/v1/watcher", response_model=WatcherState)
def set_watcher(body: WatcherUpdate, request: Request) -> WatcherState:
    watcher = _watcher(request)
    watcher.set_enabled(body.enabled)
    return _state(request)


def _state(request: Request) -> WatcherState:
    watcher = _watcher(request)
    inbox = request.app.state.inbox
    return WatcherState(
        enabled=watcher.enabled,
        paused=watcher.paused,
        sequence=inbox.sequence,
        poll_interval_seconds=watcher.interval_seconds,
    )


def _watcher(request: Request) -> ClipboardWatcher:
    watcher = getattr(request.app.state, "watcher", None)
    if not isinstance(watcher, ClipboardWatcher):
        raise RuntimeError("Watcher is not configured")
    return watcher
