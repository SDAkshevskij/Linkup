# Changelog

## Unreleased — S1 framing draft (not enabled)

- Define a bounded versioned record wire format with Linux Python and Android Java codecs and a synthetic libsodium known-answer fixture. Isolated Python/JVM/native oracle tests passed; runtime encryption, device trust, replay protection, provider integration and release remain pending. [Handoff](S1_HANDOFF.md).

## Unreleased — Activity reliability and cloud containment

- Repaired Android foreground bootstrap and Linux X11 diagnostics. Rendered local SQLite history independently of cloud access and moved source permissions into integrated controls.
- Paused native cloud Activity and required separate account-scoped consent for legacy Transfer. Restricted transport to HTTPS/WSS and public Supabase keys. The repository's Supabase schema blocks authenticated Activity inserts from old clients when applied and preserves existing rows. Added a credential-free Android connectivity probe.
- Added end-to-end Activity coverage from real OS events to the visible timeline on Android API 35 and Linux Xvfb/Openbox, plus regression scenarios for consent and unsafe transport. Physical-device and disposable-account checks are listed separately. [P0 verification](linkup-v0.2.0/P0-VERIFICATION.md)
- Verified data retention across Android signed upgrade and Linux packaged upgrade, including incompatible signing and Linux startup rollback. Verified the Activity policy change on disposable PostgreSQL. [Implementation CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46130074791)

## v0.2.1 — published

- Added main-only GitHub Releases workflow using an owner-controlled PKCS12. Both clients check release versions and download checksums. Android verifies the APK identity and signing certificate and invokes the system installer. Linux updates its per-user installation and restores the previous version if the new UI fails to load.
- Kept the Android app ID and WebView origin and the Linux user data directory and Qt WebEngine origin across upgrades. Added fixture-based upgrade and data retention tests, including Android rejection of an incompatible signing key and Linux startup rollback.
- Added separate Supabase and cloud-disk connection progress, timeouts, validation feedback and success states. Documented the debug APK transition and release-key setup. [Release guide](linkup-v0.2.0/RELEASES.md)
- Verified Android 0.2.0 → 0.2.1 on API 35 using a disposable signing key, including data retention and rejection of an incompatible signature. Verified Linux package update, data retention and startup rollback, 10 shared JS tests, 8 Linux tests and 3 Android smoke tests. v0.2.1 is available as an owner-signed GitHub Release. [CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46035377985)

- Apply Android system bar, cutout and keyboard insets at the native container. Keep the live WebView through rotation and in-process Activity recreation, preserving open dialogs, transient form state, draft files and active connection requests.
- Improve compact Android settings and composer layout and theme scrollbars in the shared UI. Verified portrait and landscape on API 35 with a simulated cutout and both navigation modes, including IME, lifecycle and signed-upgrade data retention. [CI and screenshots](https://github.com/SDAkshevskij/Linkup/actions/runs/46043809149/artifacts/15221423298)

## v0.2.0 — experimental
- Installed Android Java and Linux PyQt5 shells with local shared UI; preserved Transfer stream, history, file/text operations and cleanup.
- Added optional native usage/activity and location collection, local storage, cloud upload and colored daily timeline with device filtering.
- Added Yandex Disk as a storage choice; retained Google Drive.
- Imported code into GitHub, added emulator smoke-test CI.
- Corrected workflow directory paths and SDK package configuration. [Baseline CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46024155107) confirmed SDK setup, seven shared tests and debug APK assembly.
- Handled Android location permission failures and checked consent again before storing a fix. Scoped the lint exception to Android's Usage Access declaration, which requires user approval in system Settings.
- Verified `:app:assembleDebug`, `:app:lintDebug` (0 errors, 11 warnings), `:app:assembleDebugAndroidTest`, and `:app:connectedDebugAndroidTest` (2 passed on API 35). CI passed all 7 shared JS tests and published the tested debug APK. The Linux-agent unit tests passed locally (4). [Successful CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710) · [APK](https://github.com/SDAkshevskij/Linkup/actions/runs/46025692710/artifacts/15211278532)
- Added shared repository context and handoff instructions for ChatGPT and Codex.

## v0.1.0 — prototype
- Adaptive PWA proof of concept for Transfer, IndexedDB, Supabase and Google Drive. Superseded as the primary platform by v0.2.0.

## Next release — planned / not yet implemented

- Verify Activity collection on the owner's Android and Linux Mint devices and run two-way Transfer with a disposable cloud account.
- Enforce the LinkUp product-design rule across the app: no default-looking scrollbars/raw controls/admin-form layouts in shipped user-facing surfaces.
