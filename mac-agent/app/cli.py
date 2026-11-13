"""Command-line entry for USB and opt-in LAN HTTPS profiles."""

from __future__ import annotations

import argparse
import sys
import threading
import time
from collections.abc import Sequence
from pathlib import Path

import uvicorn

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
        return _admin_main(args)
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
    if not parsed.lan:
        host = parsed.host or "127.0.0.1"
        port = 5000 if parsed.port is None else parsed.port
        return Settings(host=host, port=port, profile="usb", config_dir=config_dir), None
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
        config_dir=config_dir,
    )
    return settings, CertificateStore(settings.resolved_config_dir)


def _serve_usb(settings: Settings) -> int:
    token_store = TokenStore()
    _print_usb_banner(settings, token_store.token)
    application = create_app(settings=settings, token_store=token_store)
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
    pairing = PairingService(
        host=settings.host,
        port=settings.port,
        pin=pin,
        devices=devices,
        clock=time.time,
        confirm=_prompt_confirm,
    )
    limiter = RateLimiter(clock=time.time)
    _print_lan_banner(settings, pin, certs)
    if open_pair:
        _print_pairing_qr(pairing)
    _start_command_thread(pairing, devices)
    application = create_app(
        settings=settings,
        devices=devices,
        pairing=pairing,
        pairing_limiter=limiter,
    )
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


def _print_usb_banner(settings: Settings, token: str) -> None:
    host, port = settings.host, settings.port
    lines = [
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
    _emit(lines)


def _print_lan_banner(settings: Settings, pin: str, certs: CertificateStore) -> None:
    from app.core.net import advertised_host

    host = advertised_host(settings.host)
    port = settings.port
    lines = [
        f"ROIPaste Mac agent listening on https://{host}:{port} (LAN profile, TLS only)",
        "macOS may show a firewall prompt; allow incoming connections for Python",
        "so the phone can reach this agent on the local network.",
        f"Certificate SPKI pin: {pin}",
        f"Short fingerprint: {short_fingerprint(pin)}",
        f"Certificate file: {certs.cert_path}",
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


def _start_command_thread(pairing: PairingService, devices: object) -> None:
    if not sys.stdin.isatty():
        return

    def loop() -> None:
        for raw in sys.stdin:
            line = raw.strip()
            if not line:
                continue
            _handle_command(line, pairing, devices)

    threading.Thread(target=loop, daemon=True, name="roipaste-stdin").start()


def _handle_command(line: str, pairing: PairingService, devices: object) -> None:
    from app.security.devices import DeviceStore

    command, _, rest = line.partition(" ")
    command = command.lower()
    if command == "pair":
        _print_pairing_qr(pairing)
        return
    if not isinstance(devices, DeviceStore):
        return
    if command == "devices":
        for record in devices.list_devices():
            state = "revoked" if record.revoked else "active"
            print(f"{record.device_id}  {record.alias}  {state}", file=sys.stdout)
        sys.stdout.flush()
        return
    if command == "revoke" and rest:
        devices.revoke(rest.strip())
        print(f"revoked {rest.strip()}", file=sys.stdout)
        sys.stdout.flush()
        return
    if command == "pause":
        devices.set_paused(True)
        print("sync paused", file=sys.stdout)
        sys.stdout.flush()
        return
    if command == "resume":
        devices.set_paused(False)
        print("sync resumed", file=sys.stdout)
        sys.stdout.flush()
        return


def _prompt_confirm(alias: str, fingerprint: str) -> bool:
    print(
        f"Pair device {alias!r} with certificate fingerprint {fingerprint}? [y/N]",
        file=sys.stdout,
    )
    sys.stdout.flush()
    try:
        answer = input().strip().lower()
    except EOFError:
        return False
    return answer in {"y", "yes"}


def _admin_main(args: Sequence[str]) -> int:
    parser = argparse.ArgumentParser(prog="python -m app")
    parser.add_argument("--config-dir", type=Path, default=None)
    sub = parser.add_subparsers(dest="group", required=True)
    devices_parser = sub.add_parser("devices")
    devices_sub = devices_parser.add_subparsers(dest="action", required=True)
    devices_sub.add_parser("list")
    revoke = devices_sub.add_parser("revoke")
    revoke.add_argument("device_id")
    devices_sub.add_parser("pause")
    devices_sub.add_parser("resume")
    cert_parser = sub.add_parser("cert")
    cert_sub = cert_parser.add_subparsers(dest="action", required=True)
    cert_sub.add_parser("reset")
    parsed = parser.parse_args(list(args))
    config_dir = parsed.config_dir or default_config_dir()
    if parsed.group == "cert" and parsed.action == "reset":
        CertificateStore(config_dir).reset()
        print(
            "Certificate removed. Restart the LAN agent and re-pair devices; old pins will fail.",
            file=sys.stdout,
        )
        return 0
    store = open_device_store(config_dir)
    if parsed.group == "devices" and parsed.action == "list":
        for record in store.list_devices():
            state = "revoked" if record.revoked else "active"
            print(f"{record.device_id}  {record.alias}  {state}", file=sys.stdout)
        return 0
    if parsed.group == "devices" and parsed.action == "revoke":
        if store.revoke(parsed.device_id) is None:
            print(f"error: unknown device {parsed.device_id}", file=sys.stderr)
            return 1
        print(f"revoked {parsed.device_id}", file=sys.stdout)
        return 0
    if parsed.group == "devices" and parsed.action == "pause":
        store.set_paused(True)
        print("sync paused", file=sys.stdout)
        return 0
    if parsed.group == "devices" and parsed.action == "resume":
        store.set_paused(False)
        print("sync resumed", file=sys.stdout)
        return 0
    return 2


def _emit(lines: list[str]) -> None:
    for line in lines:
        print(line, file=sys.stdout)
    sys.stdout.flush()
