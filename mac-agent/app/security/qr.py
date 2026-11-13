"""ASCII QR rendering for local pairing (stdout only)."""

from __future__ import annotations

import io


def render_qr_ascii(data: str) -> str:
    import qrcode  # type: ignore[import-untyped]

    qr = qrcode.QRCode(border=1)
    qr.add_data(data)
    qr.make(fit=True)
    buffer = io.StringIO()
    qr.print_ascii(out=buffer, invert=True)
    return buffer.getvalue()
