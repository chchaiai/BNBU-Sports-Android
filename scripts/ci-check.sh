#!/usr/bin/env bash
# Reproducible repository check for CI. Any failed command stops the script.
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"

fail() {
  printf 'ERROR: %s\n' "$1" >&2
  exit 1
}

command -v java >/dev/null 2>&1 || fail "JDK 17 is required."
command -v npm >/dev/null 2>&1 || fail "Node.js 20+ and npm are required."
[[ -f "$ROOT_DIR/gradlew" ]] || fail "Gradle wrapper was not found."
[[ -f "$ROOT_DIR/backend/package-lock.json" ]] || fail "backend/package-lock.json was not found."

printf '%s\n' '[1/2] Android: unit tests, lint, and debug APK'
bash "$ROOT_DIR/gradlew" \
  :app:testDebugUnitTest \
  :app:lintDebug \
  :app:assembleDebug \
  --console=plain \
  --no-daemon

[[ -s "$APK_PATH" ]] || fail "Debug APK was not created at $APK_PATH."

printf '%s\n' '[2/2] Backend: clean lockfile install, typecheck, tests, and build'
(
  cd "$ROOT_DIR/backend"
  npm ci
  npm run check
)

printf '%s\n' 'All Android and backend checks passed.'
