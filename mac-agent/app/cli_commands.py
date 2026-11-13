"""Stdin and admin command handlers for the Mac agent."""

from __future__ import annotations

import argparse
import sys
import threading
from collections.abc import Sequence
from pathlib import Path
from typing import Any

from app.config import default_config_dir
from app.security.certs import CertificateStore
from app.security.devices import DeviceStore, open_device_store
from app.security.pairing import PairingService


def start_command_thread(pairing: Any, devices: Any, watcher: Any) -> None:
    if not sys.stdin.isatty():
        return

    def loop() -> None:
        for raw in sys.stdin:
            line = raw.strip()
            if not line:
                continue
            handle_command(line, pairing, devices, watcher)

    threading.Thread(target=loop, daemon=True, name="roipaste-stdin").start()


def handle_command(line: str, pairing: Any, devices: Any, watcher: Any) -> None:
    from app.cli import _print_pairing_qr

    command, _, rest = line.partition(" ")
    command = command.lower()
    if command == "pair" and isinstance(pairing, PairingService):
        _print_pairing_qr(pairing)
        return
    if command == "watch":
        _handle_watch(rest, watcher)
        return
    if command in {"pause", "resume"} and watcher is not None and hasattr(watcher, "set_paused"):
        paused = command == "pause"
        watcher.set_paused(paused)
        if isinstance(devices, DeviceStore):
            devices.set_paused(paused)
        _println("sync paused" if paused else "sync resumed")
        return
    if not isinstance(devices, DeviceStore):
        return
    if command == "devices":
        for record in devices.list_devices():
            state = "revoked" if record.revoked else "active"
            _println(f"{record.device_id}  {record.alias}  {state}")
        return
    if command == "revoke" and rest:
        devices.revoke(rest.strip())
        _println(f"revoked {rest.strip()}")


def _handle_watch(rest: str, watcher: Any) -> None:
    if watcher is None or not hasattr(watcher, "set_enabled"):
        _println("watcher is not available")
        return
    action = rest.strip().lower()
    if action in {"on", "enable"}:
        watcher.set_enabled(True)
        _println("watcher on")
        return
    if action in {"off", "disable"}:
        watcher.set_enabled(False)
        _println("watcher off")
        return
    state = "on" if watcher.enabled else "off"
    _println(f"watcher {state}")


def admin_main(args: Sequence[str]) -> int:
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
        _println(
            "Certificate removed. Restart the LAN agent and re-pair devices; old pins will fail."
        )
        return 0
    store = open_device_store(config_dir)
    if parsed.group == "devices" and parsed.action == "list":
        for record in store.list_devices():
            state = "revoked" if record.revoked else "active"
            _println(f"{record.device_id}  {record.alias}  {state}")
        return 0
    if parsed.group == "devices" and parsed.action == "revoke":
        if store.revoke(parsed.device_id) is None:
            print(f"error: unknown device {parsed.device_id}", file=sys.stderr)
            return 1
        _println(f"revoked {parsed.device_id}")
        return 0
    if parsed.group == "devices" and parsed.action == "pause":
        store.set_paused(True)
        _println("sync paused")
        return 0
    if parsed.group == "devices" and parsed.action == "resume":
        store.set_paused(False)
        _println("sync resumed")
        return 0
    return 2


def _println(message: str) -> None:
    print(message, file=sys.stdout)
    sys.stdout.flush()
