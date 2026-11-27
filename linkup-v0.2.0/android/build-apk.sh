#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
command -v gradle >/dev/null || { echo 'Install Gradle 8.9 and Android SDK 35 (JDK 17)'; exit 1; }
gradle :app:assembleDebug
printf '\nAPK: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$PWD"
