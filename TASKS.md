# Active tasks

## Completed — get a tested Android APK
- [x] Import v0.2.0 sources and connect GitHub.
- [x] Correct root workflow paths for the nested project directory.
- [x] Initialize SDK tools and explicitly select supported SDK packages.
- [x] Confirm JS tests (7 passed) and debug APK assembly on [CI run 46025692710](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710).
- [x] Fix `MissingPermission` for cached location and `ProtectedPermissions` for the Usage Access declaration. Lint reports 0 errors and 11 warnings. [CI diagnostics](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- [x] Run `gradle :app:connectedDebugAndroidTest` on emulator API 35. Both smoke tests passed. [CI diagnostics](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211043497)
- [x] Download the tested `app-debug.apk` and verify its debug signature with `apksigner verify --verbose`. [APK artifact](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211278532)

## P0 — stable releases and in-app updates (owner priority)
- [x] Implement the Android PKCS12 signing path with application ID `io.linkup.app` and shared numeric versionCode. Keep debug and protected release CI separate.
- [x] Implement main-only GitHub Releases workflow with environment `linkup-release`, secrets `LINKUP_KEYSTORE_BASE64` and `LINKUP_KEYSTORE_PASSWORD`, and alias `linkup`. Verify signer continuity, version, and checksums before publication.
- [x] Implement Android in-app update check (GitHub Releases, compare numeric versionCode, show changelog), download with progress, verify hash and matching package/signing certificate, launch system installation confirmation. Handle network errors, cancellation, no updates, denied installation and incompatible signature.
- [x] Implement Linux in-app update check and safe user-confirmed update, preserve local data and configuration, stop/restart UI and agent as needed, verify downloaded package, rollback on failure. Prefer user-local installation without new root privileges.
- [x] Verify Android 0.2.0 → 0.2.1 with a disposable PKCS12 and Linux package 3 → 4. Both retained settings, sessions and local SQLite/IndexedDB history. Android rejected a mismatched certificate. Linux rolled back a failed startup. [CI evidence](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)
- [x] Document the one-time transition from ephemeral debug APKs, including verified copies of unsynced data before any clean installation. [Release guide](linkup-v0.2.0/RELEASES.md)
- [x] Implement separate Supabase and cloud-disk progress, deadlines, validation, visible feedback and success states. Add JS and Android WebView regression scenarios.
- [ ] Confirm cloud connection on a physical Android device with a dedicated test account. The reported device-specific cause remains unverified.
- [ ] Owner reviews the workflow, creates and backs up the signing key privately, configures the protected GitHub environment, and dispatches the first production release after merge. Never share the private key or its password in chat or Git.

## Completed — P0 Android mobile UI

- [x] Apply Android system bar, cutout and IME insets once around the WebView. Keep navigation, composer and dialog controls reachable on the compact portrait and landscape viewport.
- [x] Preserve active page/filter, scroll, dialogs, unsaved form values, composer text/files, and pending connection state across rotation, in-process Activity recreation and background/resume. Keep unsaved credentials in memory. Test that an Android Share draft is retained without importing it twice.
- [x] Style the WebView and shared scrollbars for the dark interface with a Qt WebEngine fallback. Preserve scrolling and keyboard access.
- [x] Run emulator coverage on API 35 with an emulated cutout and both navigation modes. Include the notification shade, actual IME, focus, overflow, synthetic screenshots before/after, and the signed-upgrade data retention test. [CI report and screenshots](https://github.com/SDAkshevskij/Linkup/actions/runs/46043809149/artifacts/15221423298)
- [ ] Confirm layout and keyboard behavior on a physical Android phone, including its display cutout and manufacturer keyboard.

## P1 — follow-up
- [ ] Real Linux Mint X11 runtime/package verification; Wayland deferred.
- [ ] End-to-end transfer and activity sync with test accounts; actual Android permission/GPS/background checks.
- [ ] Improve background Transfer and OAuth renewal; assess local credential storage and encryption before relying on sensitive data.

After completing an item, record evidence in STATUS.md and CHANGELOG.md. Do not mark an unexecuted test as passed.
