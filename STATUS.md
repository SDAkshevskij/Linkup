# Development status — 2026-11-27

LinkUp v0.2.0 is experimental. The Android fixes were merged into main via PR #1. [Pull request #1](https://github.com/SDAkshevskij/Linkup/pull/1) · [Successful Android CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710)

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

## Owner feedback and next action for Codex
- Physical-phone UI feedback: system shade/bars cover parts of controls and interface; rotating the phone unexpectedly exits settings and may reset other UI state (other effects not yet verified). Owner also reports an intrusive white scrollbar in the dark settings dialog (screenshot supplied). P0 now includes safe-area/IME layout, orientation and lifecycle state restoration, themed scrollbars, and mobile regression tests. Diagnose actual Android behavior before claiming a fix.
- Owner has installed and opened the tested Android APK on a physical phone and reports that Linux connects while Android stays in settings silently. Cloud connection cause is unverified.
- Owner explicitly prioritizes a safe update mechanism. Start P0 in TASKS.md: implement signing configuration, release pipeline and update UX for Android and Linux, preserving user data. Implement and test using a disposable test signing key until the owner configures the production key. Include Android connection feedback and diagnostics in the first update.
- Production signing secrets are not yet configured or verified. Proposed contract: protected GitHub environment `linkup-release`, secrets `LINKUP_KEYSTORE_BASE64`, `LINKUP_KEYSTORE_PASSWORD`, PKCS12 alias `linkup`. Read release workflow and review it before granting access to these secrets. Do not expose secrets to PR jobs or untrusted code.
- No production release or in-app updater is implemented or verified as of this handoff. Current debug build may require a one-time migration/reinstall; preserve/export unsynced data before uninstall. No need for new Supabase/Yandex credentials just to build or test updates.
