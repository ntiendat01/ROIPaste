"""Request and response models for Wi-Fi pairing."""

from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class PairCompleteRequest(BaseModel):
    enrollment_token: str = Field(..., min_length=8, max_length=256)
    device_id: str = Field(..., min_length=1, max_length=128)
    alias: str = Field(..., min_length=1, max_length=64)


class PairCompleteResponse(BaseModel):
    device_id: str
    alias: str
    credential: str
    transport: str = "lan"


class PairStartResponse(BaseModel):
    expires_at: int
    qr: dict[str, Any]
