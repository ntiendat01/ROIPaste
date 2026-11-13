"""Paired-device store: SHA-256 of credentials only, never the bearer secret."""

from __future__ import annotations

import hashlib
import hmac
import json
import os
import sys
from dataclasses import dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any, Protocol

DEVICES_FILENAME = "paired-devices.json"
DEVICES_MODE = 0o600
KEYRING_SERVICE = "roipaste"
KEYRING_USER = "paired-devices"


def hash_credential(credential: str) -> str:
    return hashlib.sha256(credential.encode("utf-8")).hexdigest()


@dataclass(frozen=True)
class DeviceRecord:
    device_id: str
    alias: str
    credential_sha256: str
    revoked: bool
    created_at: str


class DeviceStore:
    def __init__(self, backend: _Backend) -> None:
        self._backend = backend
        self._paused = False
        self._devices: dict[str, DeviceRecord] = {}
        self._load()

    @property
    def paused(self) -> bool:
        return self._paused

    def set_paused(self, paused: bool) -> None:
        self._paused = paused
        self._save()

    def list_devices(self) -> list[DeviceRecord]:
        return list(self._devices.values())

    def has_active(self) -> bool:
        return any(not item.revoked for item in self._devices.values())

    def get(self, device_id: str) -> DeviceRecord | None:
        return self._devices.get(device_id)

    def add(self, device_id: str, alias: str, credential: str) -> DeviceRecord:
        record = DeviceRecord(
            device_id=device_id,
            alias=alias,
            credential_sha256=hash_credential(credential),
            revoked=False,
            created_at=datetime.now(UTC).isoformat(),
        )
        self._devices[device_id] = record
        self._save()
        return record

    def revoke(self, device_id: str) -> DeviceRecord | None:
        current = self._devices.get(device_id)
        if current is None:
            return None
        updated = DeviceRecord(
            device_id=current.device_id,
            alias=current.alias,
            credential_sha256=current.credential_sha256,
            revoked=True,
            created_at=current.created_at,
        )
        self._devices[device_id] = updated
        self._save()
        return updated

    def find_by_credential(self, credential: str) -> DeviceRecord | None:
        digest = hash_credential(credential)
        for record in self._devices.values():
            if hmac.compare_digest(record.credential_sha256, digest):
                return record
        return None

    def _load(self) -> None:
        raw = self._backend.read()
        if not raw:
            return
        payload = json.loads(raw)
        self._paused = bool(payload.get("paused", False))
        devices = payload.get("devices", {})
        if not isinstance(devices, dict):
            return
        for device_id, item in devices.items():
            if not isinstance(item, dict):
                continue
            self._devices[device_id] = DeviceRecord(
                device_id=device_id,
                alias=str(item.get("alias", "")),
                credential_sha256=str(item.get("credential_sha256", "")),
                revoked=bool(item.get("revoked", False)),
                created_at=str(item.get("created_at", "")),
            )

    def _save(self) -> None:
        payload: dict[str, Any] = {
            "paused": self._paused,
            "devices": {
                device_id: {
                    "alias": record.alias,
                    "credential_sha256": record.credential_sha256,
                    "revoked": record.revoked,
                    "created_at": record.created_at,
                }
                for device_id, record in self._devices.items()
            },
        }
        self._backend.write(json.dumps(payload, indent=2, sort_keys=True))


class _Backend(Protocol):
    def read(self) -> str | None: ...

    def write(self, payload: str) -> None: ...


class FileDeviceBackend:
    def __init__(self, path: Path) -> None:
        self._path = path

    def read(self) -> str | None:
        if not self._path.is_file():
            return None
        return self._path.read_text(encoding="utf-8")

    def write(self, payload: str) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        self._path.write_text(payload, encoding="utf-8")
        os.chmod(self._path, DEVICES_MODE)


class KeyringDeviceBackend:
    def __init__(self, fallback: FileDeviceBackend) -> None:
        self._fallback = fallback

    def read(self) -> str | None:
        module = _keyring_module()
        if module is None:
            return self._fallback.read()
        try:
            stored = module.get_password(KEYRING_SERVICE, KEYRING_USER)
        except Exception:
            return self._fallback.read()
        return stored if stored else self._fallback.read()

    def write(self, payload: str) -> None:
        module = _keyring_module()
        if module is None:
            self._fallback.write(payload)
            return
        try:
            module.set_password(KEYRING_SERVICE, KEYRING_USER, payload)
        except Exception:
            self._fallback.write(payload)


def open_device_store(config_dir: Path, force_file: bool = False) -> DeviceStore:
    file_backend = FileDeviceBackend(config_dir / DEVICES_FILENAME)
    if force_file or sys.platform != "darwin":
        return DeviceStore(file_backend)
    return DeviceStore(KeyringDeviceBackend(file_backend))


def _keyring_module() -> Any | None:
    try:
        import keyring
    except Exception:
        return None
    return keyring
