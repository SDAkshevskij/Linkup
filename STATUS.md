# Development status — 2026-11-28

Historical validation for LinkUp v0.2.0. The Android build/lint fixes were merged into main via PR #1. [Pull request #1](https://github.com/SDAkshevskij/Linkup/pull/1) · [Successful Android CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710)

## Verified baseline (v0.2.0)
- `node --test tests/test_activity.mjs tests/test_cloud.mjs` on CI with Node 22 ran 7 tests, all passing.
- `gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` succeeded on CI with Gradle 8.9 and Java 17. Lint reported 0 errors and 11 warnings. Gradle reported `NO-SOURCE` for the Android JVM unit-test task. [CI diagnostics](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- `gradle :app:connectedDebugAndroidTest` ran on the API 35 Google APIs x86_64 emulator. Both smoke tests passed, covering local WebView startup, the native bridge, opening Activity, and importing text from Android Share. [Instrumentation report](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- Downloaded `LinkUp-Android-debug` with `gh run download 46025692710 -R SDAkshevskij/Linkup -n LinkUp-Android-debug`. `apksigner verify --verbose app-debug.apk` validated the v2 signature. APK SHA-256 is `8987b31b5aa06eef1fc6fc87d0125af147f0a9fdddbd4c932fbb5a52f181109f`. [Tested APK](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211278532)
- `python3 -m unittest discover -s tests -p 'test_agent.py'` ran locally from `linkup-v0.2.0/` and passed all 4 Linux-agent unit tests.

## Released baseline and signing

[PR #2](https://github.com/SDAkshevskij/Linkup/pull/2) merged into main at `be9590f`. The release workflow reads the 0.2.1 version from `version.properties`, builds the native packages, verifies APK signing and publishes from approved `main`. The app settings expose update checks on both platforms and separate cloud connection status for Supabase and the selected disk.

- `node --test tests/*.mjs` passed 10 tests on CI, including timeout, authentication/schema and disk-token/binding scenarios. [CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)
- `gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` passed. Lint reported 0 errors and 13 warnings. JVM unit tests reported `NO-SOURCE`. `gradle :app:connectedDebugAndroidTest` passed all 3 smoke tests on API 35. The standalone upgrade test is selected via instrumentation arguments and skipped in that smoke-test invocation. [CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)
- `bash ../tools/test-android-upgrade.sh` passed on API 35. It ran `:app:assembleRelease` and the actual release signing and packaging script with a disposable PKCS12. It verified the APK certificate and SHA-256 checksums, used `adb install -r` to upgrade versionCode 2 to 3, and confirmed preserved WebView settings and session, IndexedDB history and attachment, and native preferences and SQLite history. An APK signed by a second test key was rejected with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. [Successful upgrade CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)
- `LINKUP_TEST_WEBENGINE=1 LIBGL_ALWAYS_SOFTWARE=1 QTWEBENGINE_CHROMIUM_FLAGS=--disable-gpu xvfb-run -a /usr/bin/python3 -m unittest discover -s tests -p 'test_*.py' -v` passed all 8 Linux tests on CI. Two packaged versions retained the Qt WebEngine IndexedDB item and blob, browser settings and session, native SQLite history and native session. The rollback test confirmed that failure to launch retains the old version. [Linux CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)
- Android CI corrections included building instrumentation against each target, writing fixtures directly to IndexedDB and allowing Chromium to commit LocalStorage before stopping the baseline process. Linux CI uses Xvfb for a working Qt graphics context. [Instrumentation diagnostic](https://github.com/SDAkshevskij/Linkup/actions/runs/46033722811) · [WebView diagnostic](https://github.com/SDAkshevskij/Linkup/actions/runs/46035385606)
- The owner reported a physical Android connection problem in 0.2.0. Device-specific cause and behavior with a real cloud account still need confirmation. The regression scenarios use synthetic data.
- GitHub has the `linkup-release` environment and v0.2.1 is published. Future releases require the applicable gates and owner authorization. [Published release](https://github.com/SDAkshevskij/Linkup/releases/tag/v0.2.1) The [release guide](linkup-v0.2.0/RELEASES.md) documents the safe debug transition and protected Environment contract.

## Merged Android mobile UI

The mobile changes merged into main at `853d475`. [PR #3](https://github.com/SDAkshevskij/Linkup/pull/3)

- Apply system bar, cutout, and IME insets once at the native view container. Fit the compact composer and settings scroll container to this viewport.
- Handle rotation with the active WebView. Reattach that WebView on in-process Activity recreation to preserve dialogs, draft files, scroll position, pending requests and transient credentials in memory.
- `node --test tests/*.mjs` passed all 10 shared tests on CI. `gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` passed with 0 Lint errors and 13 warnings. Android JVM unit tests reported `NO-SOURCE`. The Linux Qt WebEngine job passed all 8 tests. [CI run](https://github.com/SDAkshevskij/Linkup/actions/runs/46043809149)
- `bash ../tools/test-android-mobile.sh` passed on API 35 at 320 dp with a simulated cutout. The gesture run passed 7 tests and skipped the separately invoked upgrade test. The three-button run passed the portrait/landscape IME and layout scenario. CI collected 8 synthetic screenshots including the baseline. [Diagnostics](https://github.com/SDAkshevskij/Linkup/actions/runs/46043809149/artifacts/15221423298)
- `bash ../tools/test-android-upgrade.sh` passed independently on API 35. VersionCode 2 → 3 retained Android preferences, SQLite activity history, WebView settings and session, and IndexedDB history and attachment. The emulator rejected an incompatible APK signature. [Upgrade log](https://github.com/SDAkshevskij/Linkup/actions/runs/46043809149)
- The diagnostic sequence fixed screenshot collection after test APK cleanup, exercised the physical emulator size so its simulated cutout reached the app, and checked reachability after ordinary page and form scrolling. The short landscape IME viewport uses a scrolling settings header. [Initial diagnostic](https://github.com/SDAkshevskij/Linkup/actions/runs/46038612229) · [Landscape IME diagnostic](https://github.com/SDAkshevskij/Linkup/actions/runs/46040961760)

## Current work and handoff

The branch `fix/activity-cloud-p0` starts at `bf1df8a`. The latest code-bearing main workflow passed at `853d475`. TASKS.md governs P0-A local Activity, P0-B legacy cloud containment, and P0-C Android connectivity and physical round-trip verification. E2EE and the local keyboard remain in P1. No production workflow is being dispatched. [Main CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46047931490)

## Owner physical-device finding — Activity reliability

The owner granted Android Usage Access but reports that no Activity statistics are visible on either the physical Android phone or Linux Mint laptop. Treat this as a P0 functional defect, not as a UI-only issue.

Code review on 2026-11-28 found:
- Android Collector.configure() resets the usage cursor and clears app/screen state when collection is enabled. Collector.collect() then depends on future UsageEvents boundaries; immediately after enabling there can be no row until a later foreground transition. Activity-page smoke tests only verify that the page/status opens; upgrade tests seed SQLite directly. There is no current end-to-end test proving real UsageEvents -> SQLite -> timeline.
- Android background collection is a periodic JobScheduler job; the UI path does call collect() when Activity is queried, so local collection should not depend on Supabase. The physical-device failure therefore needs collector-level diagnostics rather than cloud assumptions.
- Linux app.py launches agent.py, and autostart is also installed. However agent.sample() converts xprintidle/xprop/X11 failures into None without recording the cause. The UI can therefore show collection enabled while zero samples are being produced. Current Linux tests call emit() directly and do not prove active-window sampling.
- Local Activity rows are read independently of Supabase. A Supabase timeout may prevent cloud synchronization but should not by itself explain an empty local timeline.

Next executor must first make collection observable and prove real end-to-end events on Android and Linux, then redesign the Activity settings/permission UX according to AGENTS.md. Do not hide a broken collector behind prettier UI.


## Product-quality directive — 2026-11-28

The owner explicitly raised the quality bar for future implementation. Design is now a first-class acceptance criterion rather than post-functional polish, and executors are responsible for inventing a broad adversarial test matrix instead of implementing only named happy paths. PRODUCT_QUALITY.md is now the binding design/testing contract and AGENTS.md requires it to be read before work.


## Persistent executor instructions — 2026-11-28

Detailed implementation guidance that previously existed only in chat prompts has been moved into EXECUTOR_PLAYBOOK.md. Agents must read it before work; chat prompts should point to repository instructions rather than duplicate critical process requirements.
