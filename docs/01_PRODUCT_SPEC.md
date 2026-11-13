# ROIPaste — Product Requirements Document (PRD)

## 1. Goal

Build a private cross-device text clipboard companion for OnePlus Ace 5 (OxygenOS 16) and macOS. Press a button (or use Android Share sheet) to send a text snippet from Android so it is ready for Cmd+V on Mac. Transfer Mac clipboard text back to Android reliably, with user-controlled copying. No root or third-party cloud services.

## 2. Users, platforms, constraints

- Primary user: one person controlling a MacBook and an Android phone.
- Android: target OnePlus Ace 5 with OxygenOS 16; other Android 10+ only best effort.
- macOS: supported macOS version must be documented from the actual development machine; target macOS 13+ where feasible.
- Network: USB cable and ADB for development, Wi-Fi LAN via authenticated TLS for daily use.
- Text/URL only in initial release; no images, files, rich HTML, multi-device syncing, cloud relay, keyboard replacement, root or background clipboard scraping.
- Primary language: Vietnamese; use English technical terms where customary.

## 3. Core flows / priority

### P0 — Device connection and status

1. On Mac start ROIPaste Agent locally and show its current status.
2. On Android open ROIPaste and see `Disconnected / Connecting / Connected` and transport type.
3. USB prototype uses `adb reverse tcp:5000 tcp:5000`, Android app sends requests to `http://127.0.0.1:5000`; server binds Mac loopback ONLY.
4. On USB disconnect show a clear error; user can reconnect. Do not silently switch to an insecure Wi-Fi URL.
5. For Wi-Fi, pairing involves a QR containing host address, TLS certificate/public-key fingerprint and a short-lived one-time enrollment token; user confirms pairing on Mac. All later requests require authentication.

### P0 — Android to Mac (explicit action)

1. User copies text in any app, switches to ROIPaste (visible focused Activity), taps `Gửi sang Mac`.
2. Android reads current text clipboard while focused. If empty, unsupported, too large or blocked: show an actionable error; no network send.
3. Android sends to paired Mac; Mac writes text to `NSPasteboard` or `pbcopy` as an initial adapter, replies with ACK.
4. UI shows `Đã gửi`; user can paste with Cmd+V on Mac.
5. Alternative: Android ACTION_SEND `text/plain` share target can send shared text without reading background clipboard; sender must be supported.
6. Quick Settings tile (post-MVP) may bring the Activity to foreground; never read clipboard directly from TileService.

### P0 — Mac to Android (reliable manual flow)

1. User copies a text/URL on Mac.
2. Android app requests `Lấy từ Mac` and displays sanitized preview.
3. User taps `Sao chép vào clipboard Android` (or optional `Auto-copy while app visible`) and can paste in another app.
4. If device/service policy permits receiving a push, show a notification or in-app inbox; do not assume a background process can always write clipboard.
5. Successful transfers have visible ACK and duplicate suppression.

### P1 — Mac clipboard watcher and delivery

1. Mac Agent opt-in polls changeCount (native implementation) or text content hash (Python prototype) every 0.5–1 s while running.
2. Mac sends newly copied *plain text* to authenticated Android receiver while receiver is connected; if not connected, preserve at most the most recent item in memory for manual fetch, subject to settings.
3. Android foreground app can auto-copy with explicit toggle; while in background send notification/action to copy upon user interaction. Test background writing separately; never claim guaranteed always-on behavior.
4. Ignore ROIPaste-originated changes to avoid A→B→A feedback loops. Disable watcher with one tap.

### P1 — Security and usability

- Pair/unpair, rotate/revoke device credentials, one connected Mac initially.
- Pause sync instantly from both devices.
- A user can disable clipboard previews in notifications.
- Max text payload 256 KiB UTF-8, reject larger with friendly message.
- History OFF by default. No raw clipboard content in logs, crash reports, analytics or shell history.
- Mark known-sensitive copied content on Android using `ClipDescription.EXTRA_IS_SENSITIVE` where relevant, but never rely on automatic detection alone.
- No clipboard reading from password managers by background tricks.

### P2 — Later (not MVP)

- Images, files and richer MIME types.
- Mac SwiftUI menu bar UI replacing/augmenting Python tray/CLI.
- Optional encrypted local history, explicit retention and purge.
- Multi-Mac pairing, offline transfer queue, user-selectable item conflict resolution.
- Optional extension/IME explored only as separate privacy-reviewed product, not required for ROIPaste MVP.

## 4. UX surfaces

Android: Home, Pair Device, Transfer Preview, History placeholder/settings. Mac: CLI status + optional tiny local web status page in first milestone; menu bar UI in later milestone. See `06_UI_UX_SPEC.md`.

## 5. Non-functional requirements

- No networking on Android main thread; no clipboard content in console logs.
- Tap-to-send on stable connection: target visible result within 2 seconds for <=10 KiB text on LAN/USB; measure, do not guarantee.
- Lost connection yields clear retry and does not clear or overwrite either clipboard.
- Empty/unsupported content must not produce a fake success.
- Data stays on paired devices; no external telemetry or third-party API.
- Battery-friendly: no Android permanent polling of clipboard; avoid unnecessary persistent foreground service.
- Apps close normally; Mac watcher stops cleanly.

## 6. Acceptance criteria

AC01 Android focused app reads and sends nonempty UTF-8 text; Mac can Cmd+V it, including Vietnamese, emoji and multiline text.
AC02 Android Share sheet send transfers selected text without requiring access to the current clipboard.
AC03 Copy Mac text, fetch in Android, confirm/copy and paste successfully.
AC04 Duplicate detection prevents re-broadcast loops; distinct successive clips both work.
AC05 Pairing fails closed; unauthenticated requests cannot read or set clipboard.
AC06 USB mode is bound to loopback; Wi-Fi transport rejects plaintext, invalid cert and bad token.
AC07 No raw clipboard text appears in normal logs or persistent history by default.
AC08 When app backgrounds, Android clipboard reading is not attempted; failure states are truthful.
AC09 App handles connection drops, invalid JSON, huge payload, Unicode, Mac sleeping, and service restart.
AC10 Every maintained source/test code file <= 500 physical lines; test and CI fail on violation.

## 7. Out of scope / explicit non-promises

- `Copy on Android -> Cmd+V on Mac instantly without any foreground interaction` is NOT an MVP acceptance criterion.
- `Mac copy -> instantly overwrites Android clipboard forever in the background` is NOT promised.
- System-level Apple Universal Clipboard parity, biometrics, cross-network sync and support for protected app content are out of scope.

## 8. Definition of done

Buildable Android debug APK, runnable Mac Agent, documented first-run setup, automated tests, real Ace 5 test notes, no hardcoded credentials, permissions audit, 500-line guard passing, and no unverified platform claims.
