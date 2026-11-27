# Architecture

## Current layout
- `linkup-v0.2.0/android/`: Java Android app (minSdk 29, compile/target 35; AGP 8.7.3, Java 17). MainActivity hosts a bundled WebView and a constrained native bridge. Collector/CollectJob use UsageEvents, optional location and scheduled jobs.
- `linkup-v0.2.0/linux/`: Python PyQt5 Qt WebEngine shell and X11 active-window/idle agent; D-Bus unlock where available. Wayland not implemented.
- `linkup-v0.2.0/ui/`: shared HTML/CSS/JS, IndexedDB transfer storage, cloud transports, daily activity UI.
- `linkup-v0.2.0/schema.sql`: Supabase schema and RLS.
- `linkup-v0.2.0/tests/`: JS logic/transport mock tests and Python Linux agent tests.
- `.github/workflows/android.yml`: active Android CI with SDK 35, Gradle 8.9 configuration, JS tests, assemble/lint/instrumentation tests, emulator, diagnostics and APK artifact on success.

## Data flow
Local-first: native SQLite activity storage and IndexedDB transfer storage; Supabase for authorized structured records and sync, Yandex Disk or Google Drive for binary attachments. Transfer uses foreground Realtime/polling and queued changes; activity background uploads are opt-in with a separate auth session. No continuous guarantee on Android scheduling. Location is periodic and optional, not a continuous route. Android system usage access and location permissions must be granted by the owner.

## Test commands
From repository root:
```sh
cd linkup-v0.2.0
node --test tests/test_activity.mjs tests/test_cloud.mjs
python3 -m unittest discover -s tests -p 'test_agent.py'
cd android
gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
# With an authorized connected test emulator:
gradle :app:connectedDebugAndroidTest
```
See nested README.md and ANDROID-CI.md for setup details. Verify current source and tool versions before changing the workflow. No real cloud tokens are required for mock/smoke tests.
