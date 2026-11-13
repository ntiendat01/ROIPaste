# ROIPaste — Agent Implementation Plan & Tasks

## Implementation policy

Implement one milestone at a time. No fake API success, no hidden stubs marked complete, no speculative Android background permissions. Execute tests before marking a task done. Follow `../AGENT.md`; every source/test code file <=500 physical lines.

## Milestone 0 — Repository bootstrap

- [x] Create `android/` Kotlin Jetpack Compose Gradle project with documented supported versions, namespace `com.roipaste.app` (check uniqueness before public distribution), debug/release separation. Local `./gradlew testDebugUnitTest assembleDebug` passed on 2026-11-13 (Asia/Bangkok, UTC+7) with AGP 9.5.1, Gradle 9.6.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37. Play Store package-name uniqueness was not checked.
- [x] Create `mac-agent/` Python 3.12+ FastAPI project with venv, requirements lock, mypy/ruff/pytest (or equally maintained pinned choices). Local `ruff check`, `mypy app`, and `pytest` passed on Python 3.12.16.
- [x] Add `.gitignore` for Gradle outputs, Python venv, secrets, `.env`, release keys and logs.
- [x] Create `scripts/check_file_lengths.py`, CI command and tests: walk maintained source/test paths, fail if any >500 physical lines; ignore generated/build/dependency dirs only. Local pytest fixtures (500 pass / 501 fail) and a repo-wide scan passed. A GitHub Actions job is defined but has not yet run on GitHub.
- [x] Add `README.md` setup commands with tested Mac and Android versions; never commit tokens, signing keys or certificates.
- [x] Ensure `AGENT.md`, `AGENTS.md`, `CLAUDE.md` retained in repo.

**Gate:** Local clean scaffolds and the line-limit checker (500-line fixture passes, 501-line fixture fails) were verified on 2026-11-13 (Asia/Bangkok, UTC+7). The GitHub Actions workflow has been added and linted with `actionlint` 1.7.12, but it has not yet run on GitHub.

## Milestone 1 — Mac loopback server & USB transfer

- [x] Add typed models, size validation, error envelope and UUID idempotency. Covered by unit and API tests (400 envelope, 413 oversize, UUID/kind/origin validation).
- [x] Add Mac clipboard adapter with injectable mock and subprocess-safe `pbcopy/pbpaste`; preserve Unicode and multiline. Fake adapter used in tests; `MacClipboardAdapter` unit tests mock `subprocess.run`. The Darwin integration test is skipped on this Linux runner.
- [x] Implement `/v1/health`, authenticated GET/POST `/v1/clip`, `/v1/status`. Verified with pytest and a fake clipboard.
- [x] Bind only to 127.0.0.1:5000. Create per-session development token, display safely only to local user (never log to files). `Settings` rejects `0.0.0.0` and other non-loopback hosts. `python -m app` prints the token to stdout and disables the uvicorn access log. A persistent token file was not added.
- [x] Write tests for auth denial, empty clipboard, Unicode, 256 KiB guard, duplicate ID, clipboard errors, no content logging. Local `ruff check`, `mypy app`, and `pytest` passed on 2026-11-13 (Asia/Bangkok, UTC+7): 39 passed, 1 skipped (Darwin-only adapter test).
- [x] Create `scripts/connect_usb.sh` using `adb devices` and `adb reverse tcp:5000 tcp:5000` (do not call adb as root). `shellcheck` 0.11.0 passed. The script itself was not exercised against a physical device.

**Gate:** Local pytest covers unauthenticated 401, oversize 413, and fake-clipboard read/write. The real macOS gate (`curl` with a bearer token over loopback sets and reads Mac text via `pbcopy`/`pbpaste`) is still pending a user test on macOS.

## Milestone 2 — Android MVP app

- [x] Build Home UI: Connection, Send, Fetch, Preview/Copy, Sync paused, Error/Retry. Compose screens use Vietnamese `strings.xml` and immutable `HomeUiState`; verified by local debug build and unit tests on 2026-11-13 (Asia/Bangkok, UTC+7).
- [x] Build network repository and storage for Mac address/USB mode and secret. No HTTP/clipboard calls in Composables. Token is Keystore AES-GCM wrapped in DataStore (not BuildConfig).
- [x] Use ClipboardManager only while UI Activity has focus; handle null, text coercion, size and exception. Covered by `AndroidClipboardGatewayTest` (source is not called when unfocused).
- [x] `Gửi sang Mac` sends clipboard once, displays ACK and doesn't modify local text. Covered by `HomeViewModelTest`.
- [x] `Lấy từ Mac` fetches preview; `Sao chép` action writes Android clipboard. Fetch failure does not overwrite preview or clipboard. Covered by unit tests.
- [x] ACTION_SEND `text/plain` Share Receiver with strict input validation and explicit confirmation before send. Covered by `SharePayloadTest` and `ShareViewModelTest`.
- [x] Display connectivity failures, empty clipboard, unsupported data; never falsely show `Đã gửi`. Covered by ViewModel tests.
- [x] Debug-only network security config permits loopback HTTP over adb reverse; production cleartext forbidden. Debug `res/xml` allows 127.0.0.1/localhost only; main/release has cleartext disabled.
- [x] Add unit tests for ViewModel, repository (`MockWebServer`), content validation and lifecycle/focus rules. Local `./gradlew testDebugUnitTest assembleDebug lintDebug` is required before calling this done.

**Gate:** The Ace 5 ↔ Mac USB demo is still pending a user run. The Android app does not request root or any permission besides `INTERNET`.

## Milestone 3 — Secure Wi-Fi pairing

- [x] Implement Mac-side HTTPS using a generated local ECDSA P-256 certificate, with Android verifying the SPKI SHA-256 pin (custom pin-only trust manager, no trust-all). Local uvicorn + httpx TLS tests and Android MockWebServer + `HeldCertificate` pin tests passed on 2026-11-13 (Asia/Bangkok, UTC+7). A real Ace 5 + Mac Wi-Fi run has not been done.
- [x] Implement a two-minute, single-use QR enrollment challenge plus a `y/N` confirmation on the Mac (device alias and short fingerprint). The QR/JSON contains host, port, pin, enrollment token, and protocol version, never the permanent credential. Covered by mac-agent unit tests (expiry, single use, denied without confirmation, payload has no permanent secret).
- [x] Android stores the per-device credential, pin, and host in Keystore-wrapped DataStore. The Mac stores only a SHA-256 hash of the credential (Keychain via `keyring` on Darwin when available, otherwise a mode-`0600` JSON file in the user config directory). The file/Keychain threat model is documented in the README. Unpair in Settings clears Android keys.
- [x] Device revoke (`python -m app devices revoke` / stdin `revoke`, and `DELETE /v1/devices/{device_id}` for the caller's own id), pause/resume (403 `SYNC_PAUSED`), TLS pin-mismatch rejection, and no HTTP fallback on LAN are implemented and covered by unit tests. USB session tokens are rejected on the LAN profile.
- [x] QR scanning uses the Google Play services code scanner (no `CAMERA` permission), with manual paste of the pairing payload as the fallback when Play services are missing. CameraX was not used; the choice is documented in the README. The scanner itself has not been exercised on a physical device.
- [ ] Optional mDNS / local device discovery was not implemented. Pairing verification uses the host and SPKI pin from the QR/JSON, not a discovered name.
- [ ] Tests across Wi-Fi sleep/reconnect, airplane mode, and router IP changes have not been run on hardware.

**Gate:** Unit and local TLS tests show LAN HTTP is refused at config validation, unknown or revoked credentials return 401/403, and a mismatched pin fails closed. The real-device Wi-Fi gate (phone and Mac on the same LAN, QR or paste pairing, send/fetch, revoke) is still pending a user run.

## Milestone 4 — Opt-in Mac watcher + Android receive

- [ ] Implement Mac watcher with change tracking, opt-in toggle and suppression of ROIPaste writes.
- [ ] Choose receiver method based on device test: connected app stream with in-app preview; notification/manual copy while background.
- [ ] Test Android clipboard write from background on target OS, but keep automatic background copy **disabled by default and non-promised**. Never add special permissions to bypass OS behavior.
- [ ] Add backoff/connection state, battery and resource checks. Only if legally supported, add foreground service with declared type and persistent notification, obeying Android 15/16 restrictions.
- [ ] Add integration tests for echo loops, back-to-back text, competing clips and no-delivery cases.

**Gate:** Mac copy appears in ROIPaste Android UI while active, or prompt/notification while background without lost state; no echo loops.

## Milestone 5 — Polish / release

- [ ] Settings: connection info, paired device, manual pause/resume, previews on/off, revoke, build version.
- [ ] Vietnamese strings externalized (`strings.xml`), accessibility labels, large text, dark mode.
- [ ] Mac menu-bar UI optional; do not block MVP release on it.
- [ ] Run all tests, document manual QA results and known limitations.
- [ ] Verify all maintained source/test files <= 500 lines and no source file violates layering.
- [ ] Create release checklist, build instructions and screenshots (without sensitive clipboard content).

## Required developer commands (adapt exact versions after setup)

```bash
# Mac API
cd mac-agent
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
pytest

# Android
cd ../android
./gradlew testDebugUnitTest assembleDebug

# Repository line limit
cd ..
python3 scripts/check_file_lengths.py
```

## Agent output contract for each task

At the end of each coding task, report: changed files, tested commands/results, remaining risks, any OxygenOS-specific issue, and maximum source-file line count. Do not claim tests ran if they did not.
