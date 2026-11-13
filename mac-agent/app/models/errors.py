"""Protocol error envelope models."""

from pydantic import BaseModel, Field


class ErrorDetail(BaseModel):
    code: str = Field(..., min_length=1)
    message: str = Field(..., min_length=1)


class ErrorEnvelope(BaseModel):
    error: ErrorDetail
