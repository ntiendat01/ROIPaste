"""HTTP exception handlers that emit the protocol error envelope."""

from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

from app.core.errors import AppError
from app.models.errors import ErrorDetail, ErrorEnvelope

_INVALID_REQUEST = "Invalid JSON or request fields"


def _envelope(code: str, message: str) -> dict[str, object]:
    return ErrorEnvelope(error=ErrorDetail(code=code, message=message)).model_dump()


def register_exception_handlers(application: FastAPI) -> None:
    @application.exception_handler(AppError)
    async def app_error_handler(_request: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status_code,
            content=_envelope(exc.code, exc.message),
            headers=dict(exc.headers),
        )

    @application.exception_handler(RequestValidationError)
    async def validation_handler(_request: Request, _exc: RequestValidationError) -> JSONResponse:
        # Do not include the request body or field values; they may be clipboard text.
        return JSONResponse(
            status_code=400,
            content=_envelope("INVALID_REQUEST", _INVALID_REQUEST),
        )

    @application.exception_handler(StarletteHTTPException)
    async def http_exception_handler(
        _request: Request, exc: StarletteHTTPException
    ) -> JSONResponse:
        if exc.status_code == 401:
            return JSONResponse(
                status_code=401,
                content=_envelope("UNAUTHENTICATED", "Valid bearer token required"),
                headers={"WWW-Authenticate": "Bearer"},
            )
        return JSONResponse(
            status_code=exc.status_code,
            content=_envelope("HTTP_ERROR", "Request failed"),
        )
