"""ECDSA P-256 certificate generation and SPKI SHA-256 pin helpers."""

from __future__ import annotations

import hashlib
import os
from base64 import b64encode
from datetime import UTC, datetime, timedelta
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID

from app.core.net import advertised_host

CERT_FILENAME = "tls-cert.pem"
KEY_FILENAME = "tls-key.pem"
KEY_MODE = 0o600
CERT_DAYS = 3650


def spki_sha256_digest(certificate: x509.Certificate) -> bytes:
    spki = certificate.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    return hashlib.sha256(spki).digest()


def format_pin(digest: bytes) -> str:
    return "sha256/" + b64encode(digest).decode("ascii")


def pin_from_certificate(certificate: x509.Certificate) -> str:
    return format_pin(spki_sha256_digest(certificate))


def short_fingerprint(pin: str) -> str:
    body = pin.removeprefix("sha256/")
    return body[:10]


class CertificateStore:
    """Load or create a durable self-signed ECDSA P-256 certificate."""

    def __init__(self, config_dir: Path) -> None:
        self._dir = config_dir
        self._cert_path = config_dir / CERT_FILENAME
        self._key_path = config_dir / KEY_FILENAME

    @property
    def cert_path(self) -> Path:
        return self._cert_path

    @property
    def key_path(self) -> Path:
        return self._key_path

    def get_or_create(self, advertise_host: str = "127.0.0.1") -> x509.Certificate:
        if self._cert_path.is_file() and self._key_path.is_file():
            return self.load_certificate()
        return self.generate(advertise_host)

    def load_certificate(self) -> x509.Certificate:
        data = self._cert_path.read_bytes()
        return x509.load_pem_x509_certificate(data)

    def pin(self) -> str:
        return pin_from_certificate(self.load_certificate())

    def generate(self, advertise_host: str = "127.0.0.1") -> x509.Certificate:
        self._dir.mkdir(parents=True, exist_ok=True)
        key = ec.generate_private_key(ec.SECP256R1())
        host = advertised_host(advertise_host)
        subject = issuer = x509.Name(
            [x509.NameAttribute(NameOID.COMMON_NAME, "ROIPaste Mac Agent")]
        )
        now = datetime.now(UTC)
        san = _subject_alt_names(host)
        certificate = (
            x509.CertificateBuilder()
            .subject_name(subject)
            .issuer_name(issuer)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=1))
            .not_valid_after(now + timedelta(days=CERT_DAYS))
            .add_extension(san, critical=False)
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .sign(key, hashes.SHA256())
        )
        self._key_path.write_bytes(
            key.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption(),
            )
        )
        os.chmod(self._key_path, KEY_MODE)
        self._cert_path.write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
        return certificate

    def reset(self) -> None:
        for path in (self._cert_path, self._key_path):
            if path.exists():
                path.unlink()


def _subject_alt_names(host: str) -> x509.SubjectAlternativeName:
    names: list[x509.GeneralName] = [
        x509.DNSName("localhost"),
        x509.IPAddress(__import__("ipaddress").ip_address("127.0.0.1")),
        x509.IPAddress(__import__("ipaddress").ip_address("::1")),
    ]
    try:
        parsed = __import__("ipaddress").ip_address(host)
        ip_name = x509.IPAddress(parsed)
        if ip_name not in names:
            names.append(ip_name)
    except ValueError:
        if host not in {"localhost"}:
            names.append(x509.DNSName(host))
    return x509.SubjectAlternativeName(names)
