# Development status — 2026-11-27

LinkUp v0.2.0 is experimental. The Android fixes were merged into main via PR #1. [Pull request #1](https://github.com/SDAkshevskij/Linkup/pull/1) · [Successful Android CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710)

## Verified baseline (v0.2.0)
- `node --test tests/test_activity.mjs tests/test_cloud.mjs` on CI with Node 22 ran 7 tests, all passing.
- `gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` succeeded on CI with Gradle 8.9 and Java 17. Lint reported 0 errors and 11 warnings. Gradle reported `NO-SOURCE` for the Android JVM unit-test task. [CI diagnostics](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- `gradle :app:connectedDebugAndroidTest` ran on the API 35 Google APIs x86_64 emulator. Both smoke tests passed, covering local WebView startup, the native bridge, opening Activity, and importing text from Android Share. [Instrumentation report](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- Downloaded `LinkUp-Android-debug` with `gh run download 46025692710 -R SDAkshevskij/Linkup -n LinkUp-Android-debug`. `apksigner verify --verbose app-debug.apk` validated the v2 signature. APK SHA-256 is `8987b31b5aa06eef1fc6fc87d0125af147f0a9fdddbd4c932fbb5a52f181109f`. [Tested APK](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211278532)
- `python3 -m unittest discover -s tests -p 'test_agent.py'` ran locally from `linkup-v0.2.0/` and passed all 4 Linux-agent unit tests.

## P0 release work

[PR #2](https://github.com/SDAkshevskij/Linkup/pull/2) compares `feat/safe-releases` with main at `fe91957`. The release workflow reads the 0.2.1 version from `version.properties`, builds the native packages, verifies APK signing and publishes from approved `main`. The app settings expose update checks on both platforms and separate cloud connection status for Supabase and the selected disk.

- `node --test tests/*.mjs` passed 10 tests on CI, including timeout, authentication/schema and disk-token/binding scenarios. [CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)
- `gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` passed. Lint reported 0 errors and 13 warnings. JVM unit tests reported `NO-SOURCE`. `gradle :app:connectedDebugAndroidTest` passed all 3 smoke tests on API 35. The standalone upgrade test is selected via instrumentation arguments and skipped in that smoke-test invocation. [CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)
- `bash ../tools/test-android-upgrade.sh` passed on API 35. It ran `:app:assembleRelease` and the actual release signing and packaging script with a disposable PKCS12. It verified the APK certificate and SHA-256 checksums, used `adb install -r` to upgrade versionCode 2 to 3, and confirmed preserved WebView settings and session, IndexedDB history and attachment, and native preferences and SQLite history. An APK signed by a second test key was rejected with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. [Successful upgrade CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)
- `LINKUP_TEST_WEBENGINE=1 LIBGL_ALWAYS_SOFTWARE=1 QTWEBENGINE_CHROMIUM_FLAGS=--disable-gpu xvfb-run -a /usr/bin/python3 -m unittest discover -s tests -p 'test_*.py' -v` passed all 8 Linux tests on CI. Two packaged versions retained the Qt WebEngine IndexedDB item and blob, browser settings and session, native SQLite history and native session. The rollback test confirmed that failure to launch retains the old version. [Linux CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)
- Android CI corrections included building instrumentation against each target, writing fixtures directly to IndexedDB and allowing Chromium to commit LocalStorage before stopping the baseline process. Linux CI uses Xvfb for a working Qt graphics context. [Instrumentation diagnostic](https://github.com/SDAkshevskij/Linkup/actions/runs/46033722811) · [WebView diagnostic](https://github.com/SDAkshevskij/Linkup/actions/runs/46035385606)
- The owner reported a physical Android connection problem in 0.2.0. Device-specific cause and behavior with a real cloud account still need confirmation. The regression scenarios use synthetic data.
- Production key setup and publication are pending the owner. GitHub reports no deployment environments configured. The [release guide](linkup-v0.2.0/RELEASES.md) documents the safe debug transition and protected Environment contract.

- Additional feedback in main covers Android status/navigation bars and notification shade overlap, keyboard and orientation state, and the conspicuous settings scrollbar. Those items remain open in TASKS.md.

## Next action for Codex

Review [PR #2](https://github.com/SDAkshevskij/Linkup/pull/2) and the protected release workflow. After merge, the owner can configure `linkup-release` and dispatch the first release from `main`. Validate the settings feedback on a physical Android phone with a dedicated cloud test account.
