# ROIPaste — Protocol, Pairing & Threat Model

## 1. API contract v1

Base: `/v1`; JSON UTF-8. All clipboard endpoints require valid device authentication. HTTP header `Authorization: Bearer <per-device-token>`; `X-ROIPaste-Message-Id: <uuid>` for idempotency. Prefer token in OS secret storage; never log headers.

- `GET /v1/health`: `{ "status": "ok", "protocol": 1 }`. No clipboard data. Restrict metadata.
- `GET /v1/status`: authenticated `{ "paired": true, "sync_paused": false, "transport": "usb|lan" }`.
- `POST /v1/clip`: authenticated, body `{ "message_id": "uuid", "origin": "android|mac", "kind": "text", "text": "hello" }`; response `{ "accepted": true, "message_id": "uuid", "result": "clipboard_updated|queued|duplicate" }`.
- `GET /v1/clip`: authenticated, response `{ "message_id": "uuid", "origin": "mac", "kind": "text", "text": "hello" }` or `204 No Content` if unavailable/disabled. No response caching.
- `POST /v1/pair/start`: only when Mac pairing window is visibly open; returns short-lived challenge and QR payload, never a permanent secret in reusable screenshots.
- `POST /v1/pair/complete`: challenge proof and explicit approval; yields per-device scoped credential over verified TLS. Exact proof mechanism to be implemented with tested standard cryptographic primitives, not a custom homemade cipher.
- `DELETE /v1/devices/{device_id}`: authenticated Mac admin action revokes a paired device.

**Protocol note:** A USB-only token bootstrap may use a one-time QR/code displayed locally; do not reuse LAN pairing without pinned TLS verification.

## 2. Example requests

```json
{
  "message_id": "4cf0408d-8393-41a0-8706-53fa9754315f",
  "origin": "android",
  "kind": "text",
  "text": "Xin chào từ ROIPaste 👋"
}
```

Success response:

```json
{
  "accepted": true,
  "message_id": "4cf0408d-8393-41a0-8706-53fa9754315f",
  "result": "clipboard_updated"
}
```

Error envelope:

```json
{ "error": { "code": "PAYLOAD_TOO_LARGE", "message": "Text exceeds the 256 KiB limit" } }
```

## 3. Status codes

- 200 received/duplicate, 201 created, 204 clipboard empty, 400 invalid JSON or kind, 401 unauthenticated, 403 revoked/paused, 409 pairing conflict, 413 size overflow, 429 rate-limited, 503 clipboard/service unavailable.
- No misleading 200 on failed clipboard write.
- Request timeout target: connect 3 s, read 5 s; one bounded retry for safe idempotent operations only.

## 4. Threat model

Threats: malicious Wi-Fi peer reading clipboard, stolen token, lost phone, passive sniffing, clipboard passwords/OTP, log leaks, replay attacks, fake Mac server, content injection, and denial-of-service.

Required defenses:

1. **No open clipboard HTTP service on LAN.** Self-signed TLS is acceptable ONLY with out-of-band QR certificate/SPKI pin verification; reject invalid pins. No `trust-all` TLS bypass.
2. Generate cryptographically random per-device tokens, store Android in Keystore-backed encrypted storage; Mac in Keychain or OS-protected user config (Keychain preferred). Revoke and rotate.
3. Pairing opt-in on Mac with short expiration (e.g. 2 min), single use and human confirmation of device identity. IP address alone grants no trust.
4. Default clipboard watcher OFF, no persistent history, no content preview in push notifications. User can pause/unpair instantly.
5. Restrict allowed content type (`text/plain` / URL interpreted as text) and 256 KiB max; parse and validate all input; prevent control chars in logs.
6. Bind USB development HTTP to 127.0.0.1 only and use debug-only network security config; never ship LAN HTTP fallback.
7. Use idempotency IDs and bounded replay cache. Reject mismatched protocol versions; preserve privacy on errors.
8. Server should not contain arbitrary command execution, remote automation or file-upload endpoints.
9. Explicit user-visible sync state; clipboard never silently transferred to unknown device.
10. Add dependency/version scanning and test auth bypass, failed-cert, replay and oversized request cases.

## 5. Logging

Allowed: event type, timestamp, device alias, transfer byte count, truncated UUID, elapsed duration, non-sensitive error code.
Forbidden: clipboard text, auth tokens, personal URLs, QR pairing secrets, full request/response bodies or stack traces with secrets.

## 6. Android-specific security

- Clipboard access while focused; do not use AccessibilityService, IME, ADB shell hacks or overlay tricks to bypass Android clipboard privacy.
- Android 13+ clipboard preview may show content after `setPrimaryClip`; set sensitive flag where appropriate and let user explicitly control whether to copy.
- Foreground service and notification permissions require Android-specific checks; don't rely on perpetual background execution.
- Network security config: debuggable localhost exception separate from release config; default cleartext off.

## 7. Release gate

Wi-Fi sync feature remains disabled until TLS pinning, credentials, revocation, paired-device UI and negative security tests pass. For the first demo use USB + loopback token auth only.
