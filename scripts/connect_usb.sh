#!/usr/bin/env bash
# Forward the phone's localhost:5000 to the Mac agent on 127.0.0.1:5000.
# Does not use adb root and does not run adb via sudo.
set -euo pipefail

PORT="${ROIPASTE_PORT:-5000}"

if ! command -v adb >/dev/null 2>&1; then
  echo "error: adb is not installed or not on PATH" >&2
  echo "Install Android platform-tools and try again." >&2
  exit 1
fi

# Collect serials of authorized devices only (state "device").
mapfile -t devices < <(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
count="${#devices[@]}"

if [[ "${count}" -eq 0 ]]; then
  echo "error: no authorized Android device found." >&2
  echo "Connect a USB cable, enable USB debugging, and accept the authorization prompt." >&2
  echo "Current adb device list:" >&2
  adb devices >&2
  exit 1
fi

if [[ "${count}" -ne 1 ]]; then
  echo "error: expected exactly one authorized Android device, found ${count}:" >&2
  printf '  %s\n' "${devices[@]}" >&2
  echo "Disconnect extra devices or unauthorized emulators and retry." >&2
  exit 1
fi

serial="${devices[0]}"
echo "Using device ${serial}"
adb -s "${serial}" reverse "tcp:${PORT}" "tcp:${PORT}"

cat <<EOF
USB reverse is active: device tcp:${PORT} -> this computer 127.0.0.1:${PORT}

The Android debug build should call http://127.0.0.1:${PORT} (no LAN HTTP).

Verify the Mac agent first:
  curl -sS http://127.0.0.1:${PORT}/v1/health

Then start a session (python -m app) and use the printed bearer token
for /v1/status and /v1/clip. This script never runs adb as root.
EOF
