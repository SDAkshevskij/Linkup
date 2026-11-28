# LinkUp v0.2.2 stabilization and release evidence

Baseline is main at `8ee04f9` and the published v0.2.1 APK and ZIP. PRs #1–#5 and #7 and the Android/Linux, PostgreSQL and upgrade suites informed the audit. The S1 work in draft PR #6 and the keyboard remain outside this release.

## Findings and changes

| Area | Observed failure or risk | Change |
| --- | --- | --- |
| Activity collectors | Linux could insert an older sample after erase and re-enable. Wall-clock jumps below the polling gap looked like real usage. Android could retain the foreground cursor across an observed clock or Usage Access transition and queue unbounded repeated foreground jobs. | Linux serializes collection permission and history changes in SQLite and compares wall and monotonic clocks. Android resets its tracking context on observed permission changes and clock discontinuities, coalesces foreground work and cancels stopped jobs. |
| Activity view | Repeated observations inflated event counts and overlapping slices inflated per-app duration. An unanswered read prevented later periodic reads of the same day. | Deduplicate observations within a device, union per-app time within each device, retain the overall overlap union and bound reads without blocking the next polling cycle. |
| Transfer drafts and reconcile | A multi-item submission read the live account after each asynchronous write. Reconciliation used a separate read and write and could revive a hidden row. Duplicate Android Share intents shared a mutable list and a single text slot. | Snapshot submission identity and atomically commit metadata and files. Reconcile and delete in the same IndexedDB transaction. Acknowledge a single immutable Share batch and leave subsequent intents queued. |
| Transfer authorization and retries | Per-operation signals overrode client cancellation. Bodies could hang after headers. A token refresh could replace the active account. Revocation failed to cancel a client if preferences failed to persist. | Combine abort sources through body consumption, reject refreshes for a different account and revoke in memory before persistence. Scope new persisted sessions to project and login. Separated pagination from offsets and reject repeated pages. |
| Transfer cleanup | A stale response or cache write could follow deletion. | Keep local suppression of confirmed deletes. Write download cache only in a transaction that still sees an active record. Automatic retention remains local-only. |
| Cloud diagnosis | Android could report the previous probe after a retry. A single timeout message concealed the connection phase. | Correlate native probes by request ID and report DNS, TLS, connection timeout, read timeout or HTTP with elapsed time. Physical account/device cause remains open. |
| Release integrity | Earlier Linux tests manufactured the previous build from current source. A conflicting destination could be removed by the losing download. Android Back could leave a staged update. | Pin and verify the real published v0.2.1 binaries. Preserve existing files on exclusive-create conflicts and discard canceled staged Android APKs. Prepare the candidate with a disposable key and test the same-package verifier before installing. |

## Open Source First

| Reference | Decision |
| --- | --- |
| [ActivityWatch Android](https://github.com/ActivityWatch/aw-android/tree/c5096bccb9ff4d2953af3f4975b0abc34501a5e9), MPL-2.0, `UsageStatsWatcher.kt` and `SessionEventWatcher.kt` | Its session path serializes collection and gives unlocks a separate replay cursor. Retain LinkUp's Java, opt-in cutoff, deterministic event IDs and direct SQLite. Apply the bounded work and replay principles without importing Kotlin/Rust, a server or source code. |
| [ActivityWatch window](https://github.com/ActivityWatch/aw-watcher-window/tree/b14928cc944a36414b94df9b06a59b19e3c0e0b4) and [AFK](https://github.com/ActivityWatch/aw-watcher-afk/tree/e1adf55eeb33aedf423bfdbeeb4130972532dc4a), MPL-2.0 | Xlib reads the EWMH active window and WM_CLASS. AFK logic treats a lock separately from HID idle and distinguishes unlock without input. Retain current X11 tools, Cinnamon D-Bus fallback and application-only fields. Avoid Xlib/aw-client runtime dependencies and title capture. |
| [WHATWG Fetch](https://fetch.spec.whatwg.org/) and [IndexedDB 3.0](https://www.w3.org/TR/IndexedDB-3/) | Reuse browser abort, transactions and response readers. Caller/lifetime coordination uses existing AbortController without newer `AbortSignal.any` so Qt and Android share the same code. No new runtime package. |
| [Android apksigner](https://developer.android.com/tools/apksigner) and the existing LinkUp updater | Keep Android package identity checks, platform installer, the protected GitHub workflow and Linux atomic switch. CI exercises the real published assets. No key or toolchain substitution. |

## Design audit

Compared a desktop split pane with exclusive Settings panels against a continuous sheet with a compact jump rail. Chose the latter so Android users retain input focus and can reach Save, Disk and Update under the keyboard. Scoped section headings and inline account status replaced repeated configuration chrome. The rail offers 44 px targets and follows the existing dark/lime identity. Keep provider diagnostics secondary. The Transfer empty state now offers actions available on touch and desktop.

Reviewed the Transfer composer and picker entry, local/remote history and cleanup, Activity timeline and source controls, archive calendar, inspectors, consent, Settings, and both native update paths. Existing themed web scrollbars and Android Insets remain applicable. The OS picker, package installer and Usage Access screen retain native styling. Capture compact live/empty/denied Activity, landscape Activity, Settings in both orientations, actual IME, DNS failure, desktop Activity and desktop Settings. Before images are in the merged PR #7 artifacts.

## Verification matrix

| Scenario | Layer and evidence |
| --- | --- |
| Clock forward/backward and reboot, sampling gap and erase during pending work | RED/GREEN Python stale sample and download-conflict tests, Linux collector unit tests, Android elapsed clock cases. Real wake/lock and OEM restrictions require a physical device. |
| Overlap, duplicated events, nonstandard days and failed/hung reads | Seeded range/clipping oracle, per-device duplicate cases and local read deadline. Android Settings via AppOps and native events, X11 Openbox via actual windows, persisted SQLite and rendered colored tracks. |
| Transfer rollback, consent, account changes and timeouts | Android WebView runs real IndexedDB transaction conflict and Share delivery. Node tests use scripted transport faults for cancellation, stalled body, refresh account mismatch, pagination and conflict resolution. PostgreSQL runs the real legacy Activity policy in a disposable database. |
| Update and rollback | Verify published APK and ZIP SHA-256, re-sign a copy of the Android APK with a disposable key, check matching/mismatching candidate through the built-in verifier and Android install. Check IndexedDB metadata/blob, profile settings/session, native preferences, SQLite syntax and data. Linux tests the published ZIP, atomic switch, real Qt profile and failed-start rollback. |
| Visual and lifecycle | Native Android and Qt artifacts, simulated cutout, real IME, portrait and landscape, Settings failure, empty archive, and permission-revoked Activity. |
| Private owner environment | Physical Android root cause, actual Supabase and disk round trip, Mint install/login and owner signing remain release gates. The CI test APK is for disposable installs. |

## Skills applied

- **systematic-debugging + test-driven-development.** Traced persistence and asynchronous callers. Reproduced account cancellation override, refresh account replacement, stale Linux consent and archive deletion in RED tests before fixes.
- **property-based-testing.** Preserved the seeded local-day oracle and generated Transfer reconciliation cases across unsent, hidden, synced and deleted states. Tested clock drift and per-device duplication independently of the network.
- **cross-app-tests-with-uiautomator.** Reused actual foreground accessibility packages, device Settings, AppOps, Android Share intents, physical emulator rotation, IME and both navigation modes.
- **frontend-design + impeccable-text-review.** Compared mutually exclusive sections with the jump rail. Removed config eyebrow and used plain action labels. Review screenshots independently of DOM geometry assertions.
- **differential-review.** Two passes over UI, auth, data and update call sites. The second pass found Share queue loss, dynamic account reads around cache writes, and the archive-conflict cleanup boundary. Native Activity uploads remain paused. Local app data and legacy tokens remain protected by OS access controls pending S2.
- **verification-before-completion.** Record main and candidate runs, use the exact candidate SHA, inspect artifacts and distinguish live local integrations from simulated cloud responses and owner-only checks.
