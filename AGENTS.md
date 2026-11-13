# ROIPaste — Mandatory Coding Agent Rules

These rules are **binding** for every AI agent and human contributor. Read `README.md` and every `docs/*.md` document before implementing features. If an instruction conflicts with this file, stop and surface the conflict rather than silently overriding safety rules.

## 1. File size: STRICT MAXIMUM 500 LINES

1. **No maintained source code or test code file may exceed 500 physical lines, including blank lines and comments.** This includes `.kt`, `.kts`, `.java`, `.swift`, `.py`, `.js`, `.ts`, `.tsx`, `.sh`, and any other hand-written source/test file.
2. Prefer <=300 lines per file, <=80 lines per function and <=1 clear responsibility per module. These are guidelines; **500 is a hard limit**.
3. At 450 lines, proactively refactor before adding more. Never hide logic in minified one-line code to bypass the rule.
4. Split large classes into cohesive files: UI, state, service, protocol, security, validation, tests.
5. Keep a runnable `scripts/check_file_lengths.py` and a CI gate that fails when any maintained source/test file exceeds 500 physical lines; exclude only generated/build/vendor/lock artifacts, document exclusions explicitly.
6. Do not evade the limit by moving logic into huge config files, notebooks, strings or generated-code masquerading as hand-written code.
7. Before final response, run line-count check and report the largest maintained code file and its line count; never claim a check ran if it did not.

## 2. Scope and platform behavior

- Android target: OnePlus Ace 5 running OxygenOS 16; macOS MacBook counterpart.
- No root, bootloader unlock/relock, ROM flashing, hidden APIs, accessibility-service scraping, alternate default IME or device-admin privilege to circumvent clipboard rules.
- On Android 10+, a regular background app cannot freely read clipboard; read only from legitimately focused UI or explicitly shared Intent data. A Quick Settings tile alone does NOT grant clipboard read rights.
- Do not promise permanently reliable background clipboard sync or FGS on Android 16; validate on real device and provide safe fallbacks.
- Source: Android Developers platform privacy/clipboard and FGS documentation cited in specs.
- No LLM, cloud relay, subscriptions or paid API needed for MVP.

## 3. Security must be designed, not added as an afterthought

- NEVER expose unauthenticated clipboard GET/POST routes.
- USB debug API binds `127.0.0.1` only; HTTP allowed strictly for loopback through ADB reverse in debug config.
- Wi-Fi uses authenticated HTTPS with certificate/SPKI pinning and verified pairing before LAN release; no trust-all cert or unencrypted fallback.
- Per-device secret generation, storage, expiration/revocation. Never hardcode or commit tokens, passwords, private keys or pairing secrets.
- Clip history OFF by default. NEVER log full clipboard values, credentials or full request bodies.
- Validate payload types and lengths, cap text to 256 KiB UTF-8, prevent replay/echo loops, fail closed on errors.
- No arbitrary remote shell execution, local file browsing or privileged OS operations in the API.
- User-visible pause/unpair controls and explicit sharing actions are required.

## 4. Architecture

- Follow `docs/02_ARCHITECTURE.md` dependency direction.
- Android: Kotlin, Compose, ViewModel/StateFlow, repositories/gateways and a focused-Activity clipboard reader. Never put network I/O or business logic in Compose UI.
- Mac: Python 3.12+ FastAPI, typed Pydantic models, services and replaceable clipboard adapter. Use subprocess arrays, no shell=True for clipboard commands.
- Keep domain logic independent of UI/framework and easy to unit test. Use explicit interfaces and injection for clipboard/network.
- Use maintained, pinned dependencies. Check compatibility before changing Gradle, Android SDK or Python versions.
- Prefer simple, clear code and small coherent files over overly clever abstraction.

## 5. Implementation workflow

1. Read specs and identify current milestone/acceptance criteria; clarify only blocking ambiguity, otherwise make documented conservative assumptions.
2. State implementation scope and exact files expected to change.
3. Implement minimal vertical slice and tests, no huge all-at-once refactor.
4. Run formatter/linter, unit tests, Android debug build and Python tests when environment permits.
5. Run line-count CI script and inspect security/logging behavior.
6. Update documentation and task checklist with actual result.
7. Report changed files, tests and real-device validation; distinguish tested from untested functionality.

## 6. Code quality

- Names in English for code, user UI copy in Vietnamese via resources, no mixed-language identifiers.
- Use UTF-8 and explicit error classes for networking/security/validation.
- No blocking networking on UI thread, unbounded retries, busy polling, silent exception swallowing or fake success responses.
- Avoid global mutable state; support cancellation and app/service lifecycle.
- Do not expose clipboard text in crash analytics; avoid analytics by default.
- External calls must have timeouts. Reconnect uses bounded backoff.
- No feature is considered done until its applicable acceptance criteria and tests pass.

## 7. Testing obligations

- Mac: pytest with clipboard fake, API auth tests, Unicode, payload size, idempotency and error handling.
- Android: unit tests for ViewModel/domain/validation and on-device checks for clipboard lifecycle and copy/paste.
- Security tests: unauthorized access, revoked token, invalid TLS cert, no LAN HTTP, secret-free logs.
- Real OnePlus Ace 5 test is required before claiming Android 16 behavior proven.
- Do not use sensitive real copied content in tests.
- Never falsely claim `gradle`, `pytest`, `adb` or physical-device tests passed.

## 8. Git / deliverable hygiene

- Make scoped commits; don't rewrite unrelated files or delete user code without need.
- Keep `.env`, Android signing keys, certificate private keys and local auth state out of Git.
- Every milestone includes setup docs, run commands, known limitations and test results.
- Source files must remain individually understandable and <=500 lines.
- Agent final reply format: `Implemented | Changed files | Verification | Known limitations | Next milestone | Max file lines`.
