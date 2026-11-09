#!/usr/bin/env bash
# Build the app and install it on a USB-connected phone.
# Usage: ./scripts/install.sh
set -euo pipefail
cd "$(dirname "$0")/.."

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Install Android platform-tools (or the Android SDK) first." >&2
  exit 1
fi

if [ -z "$(adb devices | awk 'NR>1 && $2=="device" {print $1}')" ]; then
  echo "No phone detected." >&2
  echo "1) Enable Developer options -> USB debugging on the phone" >&2
  echo "2) Plug it in and accept the 'Allow USB debugging?' prompt" >&2
  exit 1
fi

echo "Building…"
./gradlew --no-daemon assembleDebug

APK=app/build/outputs/apk/debug/app-debug.apk
echo "Installing $APK…"
adb install -r "$APK"
adb shell am start -n com.archive.app/.ui.MainActivity 2>/dev/null || true
echo "Done. Sign in with the account you created in Settings if asked."
