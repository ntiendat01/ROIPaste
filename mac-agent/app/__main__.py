"""Entry point: python -m app. Prints the session token to stdout only."""

from __future__ import annotations

import sys

import uvicorn

from app.config import Settings
from app.main import create_app
from app.security.auth import TokenStore


def _print_session_banner(settings: Settings, token: str) -> None:
    host = settings.host
    port = settings.port
    examples = [
        f"ROIPaste Mac agent listening on http://{host}:{port}",
        "The session token is printed once below and is not written to log files.",
        "",
        "Session token:",
        f"  {token}",
        "",
        "Examples:",
        f"  curl -sS http://{host}:{port}/v1/health",
        f"  curl -sS -H 'Authorization: Bearer {token}' http://{host}:{port}/v1/status",
        "",
    ]
    for line in examples:
        print(line, file=sys.stdout)
    sys.stdout.flush()


def main() -> None:
    settings = Settings()
    token_store = TokenStore()
    _print_session_banner(settings, token_store.token)
    application = create_app(settings=settings, token_store=token_store)
    uvicorn.run(
        application,
        host=settings.host,
        port=settings.port,
        access_log=False,
        log_level="info",
    )


if __name__ == "__main__":
    main()
