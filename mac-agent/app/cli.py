"""Command-line entry for USB and opt-in LAN HTTPS profiles."""

from __future__ import annotations

import argparse
import sys
import time
from collections.abc import Sequence
from pathlib import Path

import uvicorn

from app.cli_commands import admin_main, start_command_thread
from app.cli_input import TerminalInput
from app.config import Settings, default_config_dir
from app.core.errors import InsecureLanConfigError, InvalidBindAddressError
from app.core.net import detect_private_ipv4
from app.main import create_app
from app.security.auth import TokenStore
from app.security.certs import CertificateStore, short_fingerprint
from app.security.devices import open_device_store
from app.security.pairing import PairingService
from app.security.qr import render_qr_ascii
from app.security.ratelimit import RateLimiter


def main(argv: Sequence[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    if args and args[0] in {"devices", "cert"}:
        return admin_main(args)
    return _serve_main(args)


def _serve_main(args: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m app", description="ROIPaste Mac agent")
    parser.add_argument("--lan", action="store_true", help="Opt-in LAN HTTPS profile")
    parser.add_argument(
        "--host",
        help="Bind address (USB default 127.0.0.1; LAN auto-detects a private IP)",
    )
    parser.add_argument("--port", type=int, default=None)
    parser.add_argument(
        "--pair",
        action="store_true",
        help="Open a 2-minute pairing window at startup",
    )
    parser.add_argument(
        "--watch",
        action="store_true",
        help="Opt-in Mac clipboard watcher (off by default)",
    )
    parser.add_argument(
        "--bind-any",
        action="store_true",
        help="Explicitly bind 0.0.0.0 (LAN only). The QR still advertises a private IP.",
    )
    parser.add_argument("--config-dir", type=Path, default=None)
    parsed = parser.parse_args(list(args))
    try:
        settings, certs = _build_settings(parsed)
    except (InvalidBindAddressError, InsecureLanConfigError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    if settings.profile == "usb":
        return _serve_usb(settings)
    assert certs is not None
    return _serve_lan(settings, certs, open_pair=parsed.pair)


def _build_settings(parsed: argparse.Namespace) -> tuple[Settings, CertificateStore | None]:
    config_dir = parsed.config_dir or default_config_dir()
    watch = bool(parsed.watch)
    if not parsed.lan:
        host = parsed.host or "127.0.0.1"
        port = 5000 if parsed.port is None else parsed.port
        settings = Settings(
            host=host,
            port=port,
            profile="usb",
            config_dir=config_dir,
            watcher_enabled=watch,
        )
        return settings, None
    host = parsed.host
    if host is None:
        host = "0.0.0.0" if parsed.bind_any else detect_private_ipv4()
    port = 5000 if parsed.port is None else parsed.port
    settings = Settings(
        host=host,
        port=port,
        profile="lan",
        tls_enabled=True,
        allow_unspecified_bind=parsed.bind_any,
        pairing_open=parsed.pair,
        watcher_enabled=watch,
        config_dir=config_dir,
    )
    return settings, CertificateStore(settings.resolved_config_dir)


def _serve_usb(settings: Settings) -> int:
    token_store = TokenStore()
    application = create_app(settings=settings, token_store=token_store)
    _print_usb_banner(settings, token_store.token, application.state.watcher.enabled)
    start_command_thread(None, None, application.state.watcher)
    uvicorn.run(
        application,
        host=settings.host,
        port=settings.port,
        access_log=False,
        log_level="info",
    )
    return 0


def _serve_lan(settings: Settings, certs: CertificateStore, open_pair: bool) -> int:
    certs.get_or_create(settings.host)
    pin = certs.pin()
    devices = open_device_store(settings.resolved_config_dir)
    terminal = TerminalInput()
    pairing = PairingService(
        host=settings.host,
        port=settings.port,
        pin=pin,
        devices=devices,
        clock=time.time,
        confirm=terminal.confirm,
    )
    limiter = RateLimiter(clock=time.time)
    application = create_app(
        settings=settings,
        devices=devices,
        pairing=pairing,
        pairing_limiter=limiter,
    )
    _print_lan_banner(settings, pin, certs, application.state.watcher.enabled)
    if open_pair:
        _print_pairing_qr(pairing)
    start_command_thread(pairing, devices, application.state.watcher, terminal)
    uvicorn.run(
        application,
        host=settings.host,
        port=settings.port,
        access_log=False,
        log_level="info",
        ssl_certfile=str(certs.cert_path),
        ssl_keyfile=str(certs.key_path),
    )
    return 0


def _print_usb_banner(settings: Settings, token: str, watcher_on: bool) -> None:
    host, port = settings.host, settings.port
    watch_state = "on" if watcher_on else "off"
    lines = [
        f"ROIPaste Mac agent listening on http://{host}:{port}",
        "The session token is printed once below and is not written to log files.",
        "",
        "Session token:",
        f"  {token}",
        "",
        f"Clipboard watcher is {watch_state} (opt-in; pass --watch or type `watch on`).",
        "Examples:",
        f"  curl -sS http://{host}:{port}/v1/health",
        f"  curl -sS -H 'Authorization: Bearer {token}' http://{host}:{port}/v1/status",
        "",
    ]
    _emit(lines)


def _print_lan_banner(
    settings: Settings, pin: str, certs: CertificateStore, watcher_on: bool
) -> None:
    from app.core.net import advertised_host

    host = advertised_host(settings.host)
    port = settings.port
    watch_state = "on" if watcher_on else "off"
    lines = [
        f"ROIPaste Mac agent listening on https://{host}:{port} (LAN profile, TLS only)",
        "macOS may show a firewall prompt; allow incoming connections for Python",
        "so the phone can reach this agent on the local network.",
        f"Certificate SPKI pin: {pin}",
        f"Short fingerprint: {short_fingerprint(pin)}",
        f"Certificate file: {certs.cert_path}",
        f"Clipboard watcher is {watch_state} (opt-in; pass --watch or type `watch on`).",
        "Type `pair` and press Enter (or start with --pair) to open a 2-minute window.",
        "The QR code contains a one-time enrollment token, not a permanent credential.",
        "USB loopback HTTP is unchanged: run without --lan for adb reverse.",
        "",
    ]
    _emit(lines)


def _print_pairing_qr(pairing: PairingService) -> None:
    enrollment = pairing.open_window()
    payload = pairing.qr_json(enrollment)
    _emit(
        [
            "Pairing window is open for 2 minutes.",
            "Scan this QR with ROIPaste, or paste the JSON line below.",
            "",
            render_qr_ascii(payload).rstrip(),
            "",
            "Pairing payload (no permanent secret):",
            payload,
            "",
        ]
    )


def _emit(lines: list[str]) -> None:
    for line in lines:
        print(line, file=sys.stdout)
    sys.stdout.flush()
