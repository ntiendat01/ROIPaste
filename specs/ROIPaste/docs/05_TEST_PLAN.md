# ROIPaste — QA Test Plan (OnePlus Ace 5 + MacBook)

## 1. Test setup

- Real device: OnePlus Ace 5 with OxygenOS 16, no root, normal daily-use device.
- MacBook running pinned local Agent; USB-C data cable and optional trusted Wi-Fi.
- Use dedicated benign test snippets only. Do not copy real banking, work, OTP, passwords or private data during testing.
- Track OS version, Android security patch, macOS version, APK build, agent version and network profile.

## 2. Functional test matrix

| ID | Scenario | Expected result |
|---|---|---|
| TC01 | Android copy `Xin chào` -> open ROIPaste -> Send | Mac Cmd+V yields exact text; ACK shown |
| TC02 | Android copy emoji / Vietnamese accents / multiline -> Send | Exact Unicode preserved |
| TC03 | Android sends empty clipboard | Clear warning; no Mac modification |
| TC04 | Android clipboard content >256 KiB | Local rejected; no partial clipboard update |
| TC05 | Android Share `text/plain` from browser | Shared content preview and explicit send |
| TC06 | Mac copy URL -> Android Fetch -> Copy | Android paste yields exact URL |
| TC07 | Mac clipboard empty / unsupported | 204 or clear no-content state, no false success |
| TC08 | Repeat same message ID | Duplicate suppressed, no loop |
| TC09 | Two distinct snippets sent quickly | Deterministic final state and events |
| TC10 | Disconnect USB during transfer | Actionable failure, original clipboard unchanged |
| TC11 | Mac sleeps or server restarts | Error shown; user can retry and reconnect |
| TC12 | Quick Settings tile while app backgrounded | Opens focused app, does not read in tile service |
| TC13 | Background Android app | No unauthorized clipboard reads; optional notification path tested |
| TC14 | Android app killed by system | Relaunch restores settings (not clip content) |
| TC15 | Mac clipboard watcher opt-out | No passive transfers |
| TC16 | Pause sync on either device | Transfer blocked, status clear |
| TC17 | Reconnect after Wi-Fi IP changes | Re-pair/reconnect UX correct; no insecure fallback |

## 3. Security tests

| ID | Scenario | Expected result |
|---|---|---|
| SEC01 | GET/POST `/v1/clip` without auth | 401; clipboard not read/written |
| SEC02 | Expired/revoked credential | 401/403, never accepted |
| SEC03 | TLS certificate pin mismatch | Connection fails closed |
| SEC04 | Plain HTTP Wi-Fi attempt | Not allowed in release build |
| SEC05 | Malformed JSON / wrong MIME / invalid UUID | 400; no crash |
| SEC06 | Huge request | 413; bounded memory handling |
| SEC07 | Replay message | Idempotent handling, bounded cache |
| SEC08 | Inspect logcat/Mac logs | No clipboard contents or tokens |
| SEC09 | Second unauthorized LAN host | No clipboard access |
| SEC10 | Device unpaired | Keys removed/revoked, cannot reconnect silently |

## 4. Usability and Android 16 validation

- Test with Android app in foreground, background, locked phone, screen off and battery optimization enabled.
- Observe Android 13+ system clipboard preview; ensure app doesn't add a duplicate toast.
- Test notification permission denial; manual fetch/send must still work.
- If attempted, measure reliability of background `ClipboardManager.setPrimaryClip` across screen lock, app standby and power optimization. Record observed result, not assumption.
- Ensure Quick Settings tile never assumes tile service has access to current clipboard.
- Use safe test app, not financial apps or other sensitive apps.

## 5. Performance checks

- 100 transfers of 1 KiB text, collect median/p95 round-trip over USB and LAN (numbers are measurements, not preset promises).
- 10 KiB, 128 KiB, max 256 KiB and over-limit input tests.
- Mac agent idle CPU/memory and Android standby battery impact with passive sync off/on.
- Repeated connect/disconnect 20 cycles; no growing queue or memory leak.

## 6. Automated testing

Android: JVM unit tests for domain/viewmodels; instrumented tests for clipboard when foreground; use fake network for predictable behavior. Avoid global clipboard races when tests are parallel.

Mac: pytest for API/auth/validation with fake ClipboardAdapter; integration tests for actual `pbcopy/pbpaste` on macOS; skip safely on non-Mac CI.

E2E: ADB UI smoke test on physical Ace 5; optionally Maestro flow for opening ROIPaste, tapping Send and checking confirmation. Never automate private/financial applications.

## 7. Release criteria

All P0 functional and security tests pass on the target Ace 5; no privacy regressions; zero committed secrets; source line limit passed; documented limitations for background sync; user can unpair and stop service.
