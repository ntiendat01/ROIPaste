"""Shared FastAPI dependencies."""

from fastapi import Request

from app.core.transfer import TransferService


def get_transfer_service(request: Request) -> TransferService:
    service = getattr(request.app.state, "transfer", None)
    if not isinstance(service, TransferService):
        raise RuntimeError("Transfer service is not configured")
    return service
