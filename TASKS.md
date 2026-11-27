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

## P0 — Activity must work on real devices, not only synthetic tests

- [ ] Reproduce and fix the owner's report: after permissions are granted, Activity shows no real statistics on both physical Android and Linux Mint. Do not call Activity “working” until a real application transition produces a local event and that event appears in the LinkUp timeline on each supported platform.
- [ ] Android collector: add observable health/diagnostics (usage-access state, enabled state, cursor, last collect attempt/success, last UsageEvents boundary, last app, local event count, last event timestamp, JobScheduler state/error). Fix any bootstrap gap after enabling collection: the current implementation resets cursor/app/screen at enable time and can show zero until a later ACTIVITY_RESUMED event. Make first-run behavior understandable and promptly observable without fabricating past usage. Verify collect-on-open and deferred background collection.
- [ ] Android end-to-end test: on emulator grant Usage Access via appops, create real foreground-app transitions, run Collector, and assert real app rows in SQLite and the Activity UI. Existing tests that only open the Activity page or pre-seed SQLite are not sufficient.
- [ ] Linux collector: expose agent heartbeat/PID, XDG session type, DISPLAY, xprintidle/xprop availability, last sample/error, active WM_CLASS, local event count and last event timestamp. agent.sample currently swallows X11/tool errors and returns None, while status can still look healthy. Fix this silent failure mode.
- [ ] Linux lifecycle: verify the collector process is actually running after install, launch, update and login; restart it safely if missing/stale. Test the real path from an active X11 window -> xprop/xprintidle -> collector -> SQLite -> UI, not only direct calls to emit().
- [ ] Linux Wayland: detect it explicitly. If a robust Cinnamon/Mint Wayland source can be implemented, add it; otherwise show a clear unsupported source state rather than a misleading “collection enabled”. Do not claim Linux activity support in a Wayland session until verified.
- [ ] Keep local Activity independent of Supabase. Cloud login/sync failure must never hide or block locally collected statistics. Show local and cloud health separately.
- [ ] Add a compact Activity diagnostics inspector (secondary icon/sheet, not a permanent admin form) with copyable non-secret diagnostics so physical-device failures can be diagnosed without adb or terminal spelunking.

## P0 — redesign Activity controls and enforce LinkUp visual quality

- [ ] Replace the current checkbox + separate “Системный доступ” / “Разрешение геолокации” button layout. Use designed source cards/toggles: enabling “Приложения” launches Usage Access flow when needed; enabling “Местоположение” launches runtime/system permission flow when needed; state updates automatically on resume.
- [ ] Move Supabase/background-session details into an “Облако / расширенные настройки” sub-surface. Only reveal the background-session password when cloud Activity sync is enabled and main Supabase is connected. Do not make the main Activity screen look like a settings form.
- [ ] Redesign Activity as a distinctive visual product surface rather than a basic dashboard: timeline is the hero, devices/apps and overlap are legible at a glance, controls are compact and intentional, and empty/error/permission states are visually designed. Preserve the bold dark LinkUp identity across Android and Linux.
- [ ] Audit the whole app for generic UI regressions: default/light/basic scrollbars, raw browser/Qt controls, button-per-field forms, excessive permanent buttons, low-information boxes, and desktop forms merely squeezed onto mobile. Replace with intentional custom components and progressive disclosure while preserving accessibility and responsive behavior.

## P1 — follow-up
- [ ] Real Linux Mint X11 runtime/package verification; Wayland deferred.
- [ ] End-to-end Transfer sync with test accounts; Activity verification is promoted to P0 above.
- [ ] Improve background Transfer and OAuth renewal; assess local credential storage and encryption before relying on sensitive data.

After completing an item, record evidence in STATUS.md and CHANGELOG.md. Do not mark an unexecuted test as passed.
