"""Status and health response models."""

from typing import Literal

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    status: Literal["ok"]
    protocol: Literal[1]


class StatusResponse(BaseModel):
    paired: bool
    sync_paused: bool
    transport: Literal["usb", "lan"] = Field(..., description="Active transport profile")
