# Changelog

## Planned next release (not implemented)
- Owner prioritized stable APK signing, GitHub Releases and in-app updates on Android/Linux with upgrade/data preservation tests. Android cloud connection feedback is also a P0 issue. See TASKS.md.
- Planned mobile UI fixes: system bars/shade and keyboard must not cover controls; portrait/landscape and rotation must preserve open dialogs and unsaved state; replace intrusive default scrollbar with a minimal dark-themed style. Add regression tests. Not implemented yet.

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
