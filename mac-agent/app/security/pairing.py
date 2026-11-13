"""One-time, 2-minute enrollment window and QR payload construction."""

from __future__ import annotations

import json
import secrets
from collections.abc import Callable
from dataclasses import dataclass

from app.core.errors import PairingError
from app.core.net import advertised_host
from app.security.certs import short_fingerprint
from app.security.devices import DeviceStore

ENROLLMENT_TTL_SECONDS = 120
PROTOCOL_VERSION = 1
ConfirmFn = Callable[[str, str], bool]


@dataclass(frozen=True)
class Enrollment:
    token: str
    expires_at: float
    used: bool = False


@dataclass(frozen=True)
class PairingResult:
    device_id: str
    alias: str
    credential: str


class PairingService:
    def __init__(
        self,
        *,
        host: str,
        port: int,
        pin: str,
        devices: DeviceStore,
        clock: Callable[[], float],
        confirm: ConfirmFn,
    ) -> None:
        self._host = host
        self._port = port
        self._pin = pin
        self._devices = devices
        self._clock = clock
        self._confirm = confirm
        self._enrollment: Enrollment | None = None
        self._window_open = False

    def open_window(self) -> Enrollment:
        enrollment = Enrollment(
            token=secrets.token_urlsafe(24),
            expires_at=self._clock() + ENROLLMENT_TTL_SECONDS,
            used=False,
        )
        self._enrollment = enrollment
        self._window_open = True
        return enrollment

    def close_window(self) -> None:
        self._window_open = False

    def is_open(self) -> bool:
        enrollment = self._enrollment
        if not self._window_open or enrollment is None:
            return False
        if enrollment.used or self._clock() >= enrollment.expires_at:
            return False
        return True

    def qr_payload(self, enrollment: Enrollment | None = None) -> dict[str, object]:
        current = enrollment or self._enrollment
        if current is None:
            raise PairingError("PAIRING_WINDOW_CLOSED", "Pairing window is not open", 403)
        return {
            "v": PROTOCOL_VERSION,
            "host": advertised_host(self._host),
            "port": self._port,
            "pin": self._pin,
            "enrollment": current.token,
            "exp": int(current.expires_at),
        }

    def qr_json(self, enrollment: Enrollment | None = None) -> str:
        return json.dumps(self.qr_payload(enrollment), separators=(",", ":"), sort_keys=True)

    def start(self) -> dict[str, object]:
        if not self.is_open():
            raise PairingError(
                "PAIRING_WINDOW_CLOSED",
                "Open a pairing window on the Mac first",
                403,
            )
        enrollment = self._enrollment
        assert enrollment is not None
        return {
            "expires_at": int(enrollment.expires_at),
            "qr": self.qr_payload(enrollment),
        }

    def complete(self, enrollment_token: str, device_id: str, alias: str) -> PairingResult:
        enrollment = self._require_active()
        if not secrets.compare_digest(enrollment_token, enrollment.token):
            raise PairingError("INVALID_ENROLLMENT", "Enrollment token is invalid", 401)
        self._enrollment = Enrollment(enrollment.token, enrollment.expires_at, used=True)
        self._window_open = False
        cleaned_alias = alias.strip() or "Android"
        cleaned_id = device_id.strip()
        if not cleaned_id:
            raise PairingError("INVALID_REQUEST", "device_id is required", 400)
        fingerprint = short_fingerprint(self._pin)
        if not self._confirm(cleaned_alias, fingerprint):
            raise PairingError("PAIRING_DENIED", "Pairing was not confirmed on the Mac", 403)
        credential = secrets.token_urlsafe(32)
        self._devices.add(cleaned_id, cleaned_alias, credential)
        return PairingResult(device_id=cleaned_id, alias=cleaned_alias, credential=credential)

    def _require_active(self) -> Enrollment:
        enrollment = self._enrollment
        if not self._window_open or enrollment is None:
            raise PairingError("PAIRING_WINDOW_CLOSED", "Pairing window is not open", 403)
        if enrollment.used:
            raise PairingError("ENROLLMENT_USED", "Enrollment token already used", 409)
        if self._clock() >= enrollment.expires_at:
            raise PairingError("ENROLLMENT_EXPIRED", "Enrollment token expired", 400)
        return enrollment
