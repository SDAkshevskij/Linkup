# Development status — 2026-11-27

Baseline: v0.2.0 experimental. Source resides in linkup-v0.2.0/. Latest application CI examined: [run 46024155107](https://github.com/SDAkshevskij/Linkup/actions/runs/46024155107), commit ead886217e1f84c4ed575e3298dcda79f9e8fde7.

## Verified
- GitHub repository read/write and Actions triggers work.
- SDK 35 setup succeeded in latest run.
- Shared logic tests: 7 passed, 0 failed on latest CI.
- :app:assembleDebug completed successfully; debug APK packaging succeeded.
- Earlier local Linux agent tests reported 4/4 passed; not independently rerun during this handoff.

## Blocking issues
- Baseline CI failed at `:app:lintDebug` with 2 errors and 11 warnings. The [full lint report](https://github.com/SDAkshevskij/Linkup/actions/runs/46024155107/artifacts/15211301490) identified `MissingPermission` for `getLastKnownLocation` and `ProtectedPermissions` for `PACKAGE_USAGE_STATS`. The fixes on `fix/android-ci` handle location permission failures explicitly and scope the manifest exception to Usage Access, which Android grants through Settings. Emulator results and an APK artifact are pending CI on the fix branch.
- No real-device/real-cloud integration verification yet. Transfer background sync not implemented; disk OAuth tokens currently require manual entry/renewal. No persistent APK signing key or E2EE.
- Linux Mint package/runtime unverified; X11 supported by implementation, Wayland not implemented.

## Next action for Codex
Check the latest Android Actions run on `fix/android-ci` for the build, lint and API 35 instrumentation results. Inspect diagnostics for any new failure and verify that `LinkUp-Android-debug` is downloadable after a successful run.
