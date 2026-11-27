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
- [ ] Persistent Android release signing with an owner-controlled PKCS12 keystore; stable applicationId io.linkup.app and increasing versionCode. Keep the existing debug CI separate; no release signing credentials in pull request jobs.
- [ ] Release-only workflow on reviewed main / trusted tags, protected environment `linkup-release` with secrets LINKUP_KEYSTORE_BASE64 and LINKUP_KEYSTORE_PASSWORD, alias linkup. Fail closed if secrets are absent; never fall back to debug signing. Verify APK signer and checksum, publish versioned APK and Linux installer to GitHub Releases after checks pass.
- [ ] Android in-app update check (GitHub Releases, compare numeric versionCode, show changelog), download with progress, verify hash and matching package/signing certificate, launch system installation confirmation. Handle network errors, cancellation, no updates, denied installation and incompatible signature.
- [ ] Linux in-app update check and safe user-confirmed update, preserve local data and configuration, stop/restart UI and agent as needed, verify downloaded package, rollback on failure. Prefer user-local installation without new root privileges.
- [ ] Test upgrade from two successive versions signed with the same test key: preserve settings, local SQLite/IndexedDB data and sessions; verify incompatible signatures rejected. Document one-time transition from ephemeral debug APK; do not ask user to uninstall before backup/export or checking what is cloud-synced.
- [ ] Fix Android cloud connection feedback (Supabase vs Yandex separately, progress, bounded network timeouts, useful visible error messages, validation, success confirmation). User reports desktop connects but Android remains in settings silently; exact root cause not yet established. Add regression tests and check real-device behavior.
- [ ] Owner creates and backs up signing key privately and configures protected GitHub environment. Never request private key, passwords or tokens in chat or commits.

- [ ] Android responsive UI / safe areas: fix system status bar, navigation bar, display cutout, notification shade transitions and IME overlap. Use actual WindowInsets (including displayCutout/systemBars/IME) and coordinate native WebView padding with CSS viewport/safe-area handling; avoid double insets. Keep headers, navigation, composer, settings fields, close/save/connect buttons fully visible or reachable by scrolling in portrait and landscape, including gesture and three-button navigation. No horizontal overflow; ensure dialog widths and touch targets fit small screens.
- [ ] Preserve UI state across orientation changes, Activity recreation and background/resume: selected page/filter, open settings and other dialogs, scroll position, unsaved form edits, composer text/attachments and pending connection state. Do not lose data, close settings, trigger duplicate sends/requests, or reset auth merely because device rotates. Keep unsaved sensitive credentials in appropriate transient state; never log or persist raw passwords/tokens insecurely. Investigate Android lifecycle and WebView restoration rather than locking orientation.
- [ ] Replace conspicuous default light/native scrollbar (owner screenshot shows a wide white track with arrow controls in dark UI) with unobtrusive thin dark-theme scrollbar, no bright track/arrows, matching existing palette; preferably fade when idle on mobile where supported. Preserve touch scrolling, accessibility and desktop wheel/keyboard usability. Audit all scroll containers and dialogs on Android and Linux.
- [ ] Mobile regression coverage: emulator portrait/landscape (including rotation while settings open with synthetic unsaved input and while connecting), compact viewport, system insets and keyboard, background/resume; verify no clipped or inaccessible controls, no horizontal overflow, no lost edits/duplicate operations and usable scrolling. Capture before/after screenshots in CI diagnostics without credentials and request physical-phone confirmation.

## P1 — follow-up
- [ ] Real Linux Mint X11 runtime/package verification; Wayland deferred.
- [ ] End-to-end transfer and activity sync with test accounts; actual Android permission/GPS/background checks.
- [ ] Improve background Transfer and OAuth renewal; assess local credential storage and encryption before relying on sensitive data.

After completing an item, record evidence in STATUS.md and CHANGELOG.md. Do not mark an unexecuted test as passed.
