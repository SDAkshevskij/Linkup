# Development status — 2026-11-28

## S1 record framing draft — isolated, not integrated

Second-pass review rewrote the framing API around canonical `assemble` and explicitly unauthenticated `split_untrusted`/`splitUntrusted`, fixed strict Python header canonicalization, and moved both the JVM codec test and libsodium oracle into CI. The oracle is now anchored to pinned libsodium KDF/XChaCha expected vectors and RFC 8032 Ed25519 before checking the synthetic LinkUp fixture. Fresh isolated evidence: 8 Python tests with 400 generated round trips, JVM checks with 400 generated cases, and libsodium 1.0.18 external KAT/tamper/wrong-key checks passed locally. Exact-head GitHub Actions, runtime PyNaCl/NDK crypto, authenticated trust/replay boundary and device tests remain separate gates. No production call sites, release or change to P0 containment. See [S1_HANDOFF.md](S1_HANDOFF.md) and [DIFFERENTIAL_REVIEW_REPORT.md](DIFFERENTIAL_REVIEW_REPORT.md); S1–S5 remain open.

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

The P0 implementation commit is `499a067` on `fix/activity-cloud-p0`, based on `bf1df8a`. E2EE and the local keyboard remain in P1. No production workflow was dispatched. [PR #5](https://github.com/SDAkshevskij/Linkup/pull/5) · [Implementation CI](https://github.com/SDAkshevskij/Linkup/actions/runs/46130074791) · [Verified main baseline](https://github.com/SDAkshevskij/Linkup/actions/runs/46047931490)

### Implementation and handoff

- Android reconstructs the foreground state from recent OS UsageEvents and counts time from opt-in onward. It uses the same SQLite history for collection while the screen is open and for JobScheduler. Linux distinguishes idle, unavailable tooling, stale agents and Wayland, and supervises the collector process.
- The Activity timeline reads local history directly. Source controls show permission and collector state. An optional inspector exposes local health and excludes app names and exact activity times from copied summaries.
- Native Activity upload and automatic clipboard sharing are paused. Legacy cloud Transfer requires accounts to opt in separately from login. HTTPS/WSS and public Supabase credentials are enforced. The checked-in schema also removes INSERT access to Activity for old clients and preserves owner access to existing rows. Applying the schema to a live project remains a separate owner action.
- Android settings expose an unauthenticated native network probe with separate DNS, TLS, timeout and HTTP outcomes. WebView sign-in reports auth and schema separately. The cause of the owner's phone failure remains unresolved until this path is exercised on that phone.
- After PR review, run the physical checklist on the owner's Android and a Mint X11 login with an isolated Supabase account and synthetic text/file. Retain local history when installing a compatible test build and collect the redacted report if Android fails. [Verification checklist](linkup-v0.2.0/P0-VERIFICATION.md)

### Reproducible verification

- `node --test tests/*.mjs` passed all 13 cases on CI. `python3 -m unittest discover -s tests -p 'test_*.py'` passed 10 tests locally and skipped the Qt/X11 integration cases, which require the CI display setup.
- `gradle :app:assembleDebug :app:lintDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest` passed with 0 errors and 10 Lint warnings. The JVM unit-test task reported `NO-SOURCE`. [Android reports](https://github.com/SDAkshevskij/Linkup/actions/runs/46129655786/artifacts/15256574325)
- `bash ../tools/test-android-mobile.sh` passed on API 35 at 320 dp. Gesture navigation passed 10 tests with the separate upgrade case skipped. Three-button navigation passed its layout/IME test. The offline UsageEvents test checked native JobScheduler, SQLite, rendered history, revocation and the recovery action. The WebView test exercised legacy opt-in, opt-out and local transfer against mocked responses. The native DNS error used the operating system resolver. [Reports and screenshots](https://github.com/SDAkshevskij/Linkup/actions/runs/46129655786/artifacts/15256574325)
- `bash ../tools/test-android-upgrade.sh` passed with a disposable signing key and versions 2 → 3. It retained Android preferences, local SQLite history, WebView settings/session and IndexedDB history/blob. Android rejected the separately signed APK. [Upgrade run and APK](https://github.com/SDAkshevskij/Linkup/actions/runs/46129655786)
- `LINKUP_TEST_WEBENGINE=1 LINKUP_TEST_X11=1 LIBGL_ALWAYS_SOFTWARE=1 QTWEBENGINE_CHROMIUM_FLAGS=--disable-gpu xvfb-run -a /usr/bin/python3 -m unittest discover -s tests -p 'test_*.py' -v` passed all 12 tests. It rendered the real X11 sample after collector restart and verified packaged upgrade storage and rollback. [Linux timeline screenshot](https://github.com/SDAkshevskij/Linkup/actions/runs/46130070300/artifacts/15257372229)
- `psql -X -d postgres -f tests/test_activity_policy.sql` passed on disposable PostgreSQL. The fixture verifies legacy upload denial, owner access and isolation from a second account. [Linux and PostgreSQL job](https://github.com/SDAkshevskij/Linkup/actions/runs/46130074791/job/140933061754)


## Product-quality directive — 2026-11-28

The owner explicitly raised the quality bar for future implementation. Design is now a first-class acceptance criterion rather than post-functional polish, and executors are responsible for inventing a broad adversarial test matrix instead of implementing only named happy paths. PRODUCT_QUALITY.md is now the binding design/testing contract and AGENTS.md requires it to be read before work.


## Persistent executor instructions — 2026-11-28

Detailed implementation guidance that previously existed only in chat prompts has been moved into EXECUTOR_PLAYBOOK.md. Agents must read it before work; chat prompts should point to repository instructions rather than duplicate critical process requirements.


## Open Source First — 2026-11-28

Added OPEN_SOURCE_FIRST.md as a binding planning and implementation policy, with initial public upstream research mapped to current P0, S1–S5, local keyboard, clipboard, background Transfer, Activity design, tests and optional expansion. This is candidate research only, no source imports or builds. Next P0 executor should inspect ActivityWatch collector source/tests, evaluate native Android HTTP diagnostics and local Supabase/pgTAP tests before implementing. All existing security gates and design/test requirements remain in force.


## Repo-local Agent Skills — 2026-11-28

Eight selected skills installed under .agents/skills, pinned to upstream revisions with license/provenance. SKILLS_POLICY.md defines mandatory task routing, evidence and safety constraints; AGENTS.md and EXECUTOR_PLAYBOOK.md require it. Impeccable is a text-only local adaptation (no downloaded engine or hooks). No app dependencies, code, release, or CI changes. Actual skill discovery/invocation in Codex needs a fresh session from the updated checkout.
