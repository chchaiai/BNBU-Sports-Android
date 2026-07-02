#!/bin/bash
# CI check script for BNBU Student Android app (AND-012)
# Runs lint, unit tests, and assembleDebug — suitable for CI pipelines.
# Exit code: 0 on success, non-zero on failure.
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

cd "$PROJECT_DIR"

echo "=== BNBU Student Android CI Check ==="
echo "Project: $PROJECT_DIR"
echo ""

# 1. Kotlin lint check
echo "--- Running lint (compile check) ---"
# We run compileDebugKotlin as a fast lint proxy — catches syntax errors,
# type mismatches, missing imports, etc.
./gradlew :app:compileDebugKotlin --console=plain --no-daemon --quiet
echo "✓ Kotlin compilation passed"

# 2. Unit tests
echo ""
echo "--- Running unit tests ---"
./gradlew :app:testDebugUnitTest --console=plain --no-daemon
echo "✓ Unit tests passed"

# 3. Assemble debug APK (catches resource/Manifest issues)
echo ""
echo "--- Assembling debug APK ---"
./gradlew :app:assembleDebug --console=plain --no-daemon --quiet
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_PATH" ]; then
  APK_SIZE=$(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH" 2>/dev/null || wc -c < "$APK_PATH")
  echo "✓ Debug APK assembled ($APK_SIZE bytes)"
else
  echo "✗ Debug APK not found at $APK_PATH"
  exit 1
fi

echo ""
echo "=== All CI checks passed ==="

# Optional: try release build (may need signing config)
# ./gradlew :app:assembleRelease --console=plain --no-daemon
