# Architecture

## Current layout
- `linkup-v0.2.0/android/`: Java Android app (minSdk 29, compile/target 35; AGP 8.7.3, Java 17). MainActivity hosts a bundled WebView and a constrained native bridge. Collector/CollectJob use UsageEvents, optional location and scheduled jobs.
- `linkup-v0.2.0/linux/`: Python PyQt5 Qt WebEngine shell and X11 active-window/idle agent; D-Bus unlock where available. Wayland not implemented.
- `linkup-v0.2.0/ui/`: shared HTML/CSS/JS, IndexedDB transfer storage, cloud transports, daily activity UI.
- `linkup-v0.2.0/schema.sql`: Supabase schema and RLS.
- `linkup-v0.2.0/tests/`: JS logic/transport mock tests and Python Linux agent tests.
- `.github/workflows/android.yml`: Android SDK 35 build, lint, JS tests, emulator smoke and signed upgrade tests, Linux Qt WebEngine upgrade tests, and diagnostic artifacts.
- `.github/workflows/release.yml`: main-only release pipeline. Keeps release credentials in the protected environment and publishes the signed APK, the Linux installer and SHA-256 checksums after CI.
- `linkup-v0.2.0/version.properties`: shared release name and numeric version code.

## Data flow
Local-first: native SQLite activity storage and IndexedDB transfer storage; Supabase for authorized structured records and sync, Yandex Disk or Google Drive for binary attachments. Transfer uses foreground Realtime/polling and queued changes; activity background uploads are opt-in with a separate auth session. No continuous guarantee on Android scheduling. Location is periodic and optional, not a continuous route. Android system usage access and location permissions must be granted by the owner.

## Updates and local data

Both native shells read stable releases from the LinkUp GitHub Releases API and check the SHA-256 digest of their platform asset. Android also checks the package ID, numeric version and signing certificate before passing its private cached APK to the system installer. It retains the app ID and WebView origin. Linux stages code under `~/.local/opt/linkup/versions`, switches `current` atomically and rolls back failed startup. Its SQLite database, persistent Qt profile and localhost origin are retained. [Release and upgrade guide](linkup-v0.2.0/RELEASES.md)

## Test commands
From repository root:
```sh
cd linkup-v0.2.0
node --test tests/*.mjs
python3 -m unittest discover -s tests -p 'test_*.py'
LINKUP_TEST_WEBENGINE=1 QT_QPA_PLATFORM=offscreen QTWEBENGINE_CHROMIUM_FLAGS=--disable-gpu \
  /usr/bin/python3 -m unittest discover -s tests -p 'test_*.py'
cd android
gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
# With an authorized connected test emulator:
gradle :app:connectedDebugAndroidTest
# This script resets LinkUp on the connected test emulator:
bash ../tools/test-android-upgrade.sh
```
See nested README.md and ANDROID-CI.md for setup details. Verify current source and tool versions before changing the workflow. No real cloud tokens are required for mock/smoke tests.
