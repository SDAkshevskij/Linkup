# Development status — 2026-11-27

LinkUp v0.2.0 is experimental. The Android fixes are in commit `1945408` on `fix/android-ci`. [Pull request #1](https://github.com/SDAkshevskij/Linkup/pull/1) · [Successful Android CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710)

## Verified
- `node --test tests/test_activity.mjs tests/test_cloud.mjs` on CI with Node 22 ran 7 tests, all passing.
- `gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` succeeded on CI with Gradle 8.9 and Java 17. Lint reported 0 errors and 11 warnings. Gradle reported `NO-SOURCE` for the Android JVM unit-test task. [CI diagnostics](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- `gradle :app:connectedDebugAndroidTest` ran on the API 35 Google APIs x86_64 emulator. Both smoke tests passed, covering local WebView startup, the native bridge, opening Activity, and importing text from Android Share. [Instrumentation report](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- Downloaded `LinkUp-Android-debug` with `gh run download 46025692710 -R SDAkshevskij/Linkup -n LinkUp-Android-debug`. `apksigner verify --verbose app-debug.apk` validated the v2 signature. APK SHA-256 is `8987b31b5aa06eef1fc6fc87d0125af147f0a9fdddbd4c932fbb5a52f181109f`. [Tested APK](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211278532)
- `python3 -m unittest discover -s tests -p 'test_agent.py'` ran locally from `linkup-v0.2.0/` and passed all 4 Linux-agent unit tests.

## Remaining work

- The emulator smoke tests use synthetic data. Real-device permission revocation, GPS, background collection, and integration with test cloud accounts still need validation.
- CI uses a temporary debug signing key. Persistent APK signing and upgrade validation are the next roadmap item.
- Transfer background sync, automated disk OAuth renewal, E2EE, and Linux Mint X11 packaging and runtime validation remain open. Wayland is deferred.

## Next action for Codex
Review and merge [PR #1](https://github.com/SDAkshevskij/Linkup/pull/1) after its checks pass. Continue with persistent Android signing and upgrade validation using an owner-controlled key outside the repository.
