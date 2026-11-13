"""Request and response models for the opt-in clipboard watcher."""

from pydantic import BaseModel, Field


class WatcherUpdate(BaseModel):
    enabled: bool


class WatcherState(BaseModel):
    enabled: bool
    paused: bool
    sequence: int = Field(..., ge=0)
    poll_interval_seconds: float
