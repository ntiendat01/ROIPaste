# ROIPaste — Agent Implementation Plan & Tasks

## Implementation policy

Implement one milestone at a time. No fake API success, no hidden stubs marked complete, no speculative Android background permissions. Execute tests before marking a task done. Follow `../AGENT.md`; every source/test code file <=500 physical lines.

## Milestone 0 — Repository bootstrap

- [ ] Create `android/` Kotlin Jetpack Compose Gradle project with documented supported versions, namespace `com.roipaste.app` (check uniqueness before public distribution), debug/release separation.
- [ ] Create `mac-agent/` Python 3.12+ FastAPI project with venv, requirements lock, mypy/ruff/pytest (or equally maintained pinned choices).
- [ ] Add `.gitignore` for Gradle outputs, Python venv, secrets, `.env`, release keys and logs.
- [ ] Create `scripts/check_file_lengths.py`, CI command and tests: walk maintained source/test paths, fail if any >500 physical lines; ignore generated/build/dependency dirs only.
- [ ] Add `README.md` setup commands with tested Mac and Android versions; never commit tokens, signing keys or certificates.
- [ ] Ensure `AGENT.md`, `AGENTS.md`, `CLAUDE.md` retained in repo.

**Gate:** clean build scaffolds + line-limit checker fails on deliberate 501-line fixture and passes on 500-line fixture.

## Milestone 1 — Mac loopback server & USB transfer

- [ ] Add typed models, size validation, error envelope and UUID idempotency.
- [ ] Add Mac clipboard adapter with injectable mock and subprocess-safe `pbcopy/pbpaste`; preserve Unicode and multiline.
- [ ] Implement `/v1/health`, authenticated GET/POST `/v1/clip`, `/v1/status`.
- [ ] Bind only to 127.0.0.1:5000. Create per-session development token, display safely only to local user (never log to files).
- [ ] Write tests for auth denial, empty clipboard, Unicode, 256 KiB guard, duplicate ID, clipboard errors, no content logging.
- [ ] Create `scripts/connect_usb.sh` using `adb devices` and `adb reverse tcp:5000 tcp:5000` (do not call adb as root).

**Gate:** curl with valid bearer token over loopback sets and reads Mac text; unauthenticated/oversize requests fail.

## Milestone 2 — Android MVP app

- [ ] Build Home UI: Connection, Send, Fetch, Preview/Copy, Sync paused, Error/Retry.
- [ ] Build network repository and storage for Mac address/USB mode and secret. No HTTP/clipboard calls in Composables.
- [ ] Use ClipboardManager only while UI Activity has focus; handle null, text coercion, size and exception.
- [ ] `Gửi sang Mac` sends clipboard once, displays ACK and doesn't modify local text.
- [ ] `Lấy từ Mac` fetches preview; `Sao chép` action writes Android clipboard.
- [ ] ACTION_SEND `text/plain` Share Receiver with strict input validation and explicit confirmation before send.
- [ ] Display connectivity failures, empty clipboard, unsupported data; never falsely show `Đã gửi`.
- [ ] Debug-only network security config permits loopback HTTP over adb reverse; production cleartext forbidden.
- [ ] Add unit tests for ViewModel, repository fake, content validation and lifecycle/focus rules.

**Gate:** manual Ace 5 ↔ Mac demo works over USB; Android app never needs root.

## Milestone 3 — Secure Wi-Fi pairing

- [ ] Implement Mac-side HTTPS using generated local certificate + certificate/SPKI fingerprint verification in Android.
- [ ] Implement one-time limited-lived QR enrollment challenge plus manual approval on Mac. No permanent credentials embedded in QR.
- [ ] Save Android credential securely and Mac paired-device secret in OS-protected storage; document any threat model compromise.
- [ ] Implement device revoke, pause, TLS invalid-cert rejection and no insecure fallback.
- [ ] Implement QR scanner via system scanning contract or CameraX only after permissions and feasibility review.
- [ ] Local device discovery optional (mDNS) but pairing verification never depends on name/IP.
- [ ] Test across Wi-Fi sleep/reconnect, airplane mode and router IP changes.

**Gate:** LAN packets cannot reveal clipboard, random LAN client cannot use clipboard endpoints, invalid certificate fails.

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
