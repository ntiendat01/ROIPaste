"""Certificate generation and SPKI pin stability."""

from pathlib import Path

from app.security.certs import KEY_MODE, CertificateStore, pin_from_certificate


def test_pin_is_stable_for_the_same_certificate(tmp_path: Path) -> None:
    store = CertificateStore(tmp_path)
    first = store.generate("127.0.0.1")
    pin = pin_from_certificate(first)
    assert pin.startswith("sha256/")
    assert store.pin() == pin
    reloaded = CertificateStore(tmp_path)
    assert reloaded.pin() == pin
    assert pin_from_certificate(reloaded.load_certificate()) == pin


def test_new_key_produces_a_different_pin(tmp_path: Path) -> None:
    first = CertificateStore(tmp_path / "a")
    second = CertificateStore(tmp_path / "b")
    first.generate("127.0.0.1")
    second.generate("127.0.0.1")
    assert first.pin() != second.pin()


def test_private_key_is_mode_0600(tmp_path: Path) -> None:
    store = CertificateStore(tmp_path)
    store.generate("127.0.0.1")
    assert store.key_path.exists()
    assert store.key_path.stat().st_mode & 0o777 == KEY_MODE


def test_reset_removes_files(tmp_path: Path) -> None:
    store = CertificateStore(tmp_path)
    store.generate("127.0.0.1")
    store.reset()
    assert not store.cert_path.exists()
    assert not store.key_path.exists()
