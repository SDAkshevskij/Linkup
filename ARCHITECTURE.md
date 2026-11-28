# Architecture

## Current layout
- `linkup-v0.2.0/android/`: Java Android app (minSdk 29, compile/target 35; AGP 8.7.3, Java 17). MainActivity hosts a bundled WebView and a constrained native bridge. Collector/CollectJob use UsageEvents, optional location and scheduled jobs.
- `linkup-v0.2.0/linux/`: Python PyQt5 Qt WebEngine shell and X11 active-window/idle agent; D-Bus unlock where available. Wayland not implemented.
- `linkup-v0.2.0/ui/`: shared HTML/CSS/JS, IndexedDB transfer storage, cloud transports, daily activity UI.
- `linkup-v0.2.0/schema.sql`: Supabase schema and RLS.
- `linkup-v0.2.0/tests/`: JS logic/transport mock tests and Python Linux agent tests.
- `.github/workflows/android.yml` builds and lints the APK, runs JS tests, Android emulator and signed upgrade tests, native X11 and Qt upgrade tests, and tests the Activity policy in PostgreSQL. It publishes diagnostic artifacts.
- `.github/workflows/release.yml`: main-only release pipeline. Keeps release credentials in the protected environment and publishes the signed APK, the Linux installer and SHA-256 checksums after CI.
- `linkup-v0.2.0/version.properties`: shared release name and numeric version code.

## Data flow

Activity reads only the native SQLite event store. Android initializes foreground state from recent real UsageEvents and counts time from the opt-in cursor. It collects on opening the day view and in a periodic JobScheduler job. Linux samples idle time and the active X11 window from a single locked agent process. Its UI supervises the process and shows source health and Wayland limitations. Location remains optional on Android. Native Activity upload and refresh credentials are removed from the active collection path. Existing local and server history are left in place.

Transfer uses IndexedDB for local text and files. Supabase login can validate access to the schema without starting synchronization. Legacy cloud Transfer requires a separate opt-in scoped to the account and project. The cloud path requires HTTPS/WSS and a public key. Local drafts created while opt-out stay local, and automatic expiry applies to local history. Clipboard input is manual. The common E2EE transport remains P1. The Android connectivity inspector compares the WebView login/schema stage with an unauthenticated native HTTPS probe. Reapplying the checked-in Supabase schema revokes the legacy Activity INSERT grant and policy and retains owner-scoped reads and deletes of historical rows.

TASKS.md sets active priorities. Baseline smoke and upgrade evidence does not establish physical-device Activity or cloud reachability. [Test matrix and physical checks](linkup-v0.2.0/P0-VERIFICATION.md)

## Android window and UI lifecycle

Android applies stable system bar and cutout insets and the visible IME inset to the native container. The WebView fits within that measured viewport, so the shared stylesheet can size the composer and dialogs without adding system insets. Rotation resizes the Activity in place. In-process Activity recreation reattaches the live WebView and rebinds its bridge to the new Activity. DOM state, pending connections, draft attachments and unsaved credentials stay in memory. Durable data still uses the existing SQLite, IndexedDB and WebView settings stores.

## Updates and local data

Both native shells read stable releases from the LinkUp GitHub Releases API and check the SHA-256 digest of their platform asset. Android also checks the package ID, numeric version and signing certificate before passing its private cached APK to the system installer. It retains the app ID and WebView origin. Linux stages code under `~/.local/opt/linkup/versions`, switches `current` atomically and rolls back failed startup. Its SQLite database, persistent Qt profile and localhost origin are retained. [Release and upgrade guide](linkup-v0.2.0/RELEASES.md)

## Test commands
From repository root:
```sh
cd linkup-v0.2.0
node --test tests/*.mjs
python3 -m unittest discover -s tests -p 'test_*.py'
LINKUP_TEST_WEBENGINE=1 LINKUP_TEST_X11=1 LIBGL_ALWAYS_SOFTWARE=1 \
  QTWEBENGINE_CHROMIUM_FLAGS=--disable-gpu xvfb-run -a \
  /usr/bin/python3 -m unittest discover -s tests -p 'test_*.py' -v
cd android
gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
# With an authorized connected test emulator:
gradle :app:connectedDebugAndroidTest
# These scripts reset LinkUp on a disposable emulator and change its display settings for the mobile suite:
bash ../tools/test-android-mobile.sh
bash ../tools/test-android-upgrade.sh
```
See nested README.md and ANDROID-CI.md for setup details. The PostgreSQL test runs `psql -X -d postgres -f tests/test_activity_policy.sql` on a fresh CI runner. Use a disposable cluster for local runs because the fixture sets up Supabase-compatible roles. No real cloud tokens are required for the automated tests.


## Verification philosophy

PRODUCT_QUALITY.md defines the project-wide test and design acceptance bar. Test lists in this file are minimum commands, not a complete plan. Each change must derive additional cases from its own state transitions, platform APIs, persistence, concurrency, retries and failure modes.

For native Activity collection specifically, proof requires the real source path:
- Android: actual UsageEvents -> Collector -> SQLite -> native bridge -> Activity UI.
- Linux X11: actual active window/xprintidle/xprop -> agent -> SQLite -> Qt bridge -> Activity UI.

Synthetic rows, direct emit() calls and pre-seeded databases are useful lower-level tests but are not evidence that collection itself works.
