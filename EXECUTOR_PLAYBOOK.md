# LinkUp executor playbook

This is persistent operational guidance for Codex and any other implementation agent. Read it together with AGENTS.md and PRODUCT_QUALITY.md before planning or coding. Do not rely on chat prompts as the source of truth.

## 1. How to approach implementation

Do not optimize for the fastest obvious implementation. For every substantial user-facing task:

1. Restate the actual user problem in product terms.
2. Inspect the existing implementation and verify the current behavior before assuming documentation is correct.
3. Identify the state machine, persistence, OS integrations, network dependencies, lifecycle events, concurrency and failure modes touched by the change.
4. Derive a test matrix before coding.
5. For user-facing work, internally develop at least two materially different interaction/composition concepts. They must differ in structure or interaction, not merely colors, spacing or rounded corners.
6. Choose the concept that is most understandable, fast, distinctive and robust.
7. Implement the smallest coherent version of that concept without falling back to generic form/dashboard UI.
8. Add automated tests at the appropriate layers and at least one real end-to-end path for each critical OS integration where technically possible.
9. Inspect screenshots/artifacts, not only test exit codes.
10. Update repository handoff docs with what is proven, what is inferred and what still requires physical-device validation.

If the obvious implementation is visually generic or operationally awkward, reject it before coding.

## 2. Design expectations

The owner explicitly wants LinkUp to avoid lazy, generic, implementation-driven UI. Functionality and clarity are required, but they do not justify visually obvious or uninspired interfaces.

The target is a design that a competent engineer would not arrive at by simply stacking controls vertically. It should feel deliberately composed and recognizably LinkUp while remaining immediately understandable.

### Required behavior
- Treat design as architecture, not polish.
- Start from hierarchy and interaction, then component markup.
- Use one dominant visual idea per screen.
- Prefer progressive disclosure over permanent controls.
- Integrate permissions/actions into the feature they belong to.
- Keep primary actions visible and secondary/advanced/diagnostic actions behind deliberate secondary surfaces.
- Prefer spatial/visual encodings when they communicate state faster than labels and boxes.
- Use motion only to explain transitions/state changes.
- Design separately for phone and desktop constraints; do not merely scale the desktop layout down.
- Design empty, loading, offline, denied, degraded, error and recovery states deliberately.
- Maintain accessibility: contrast, focus, keyboard navigation, reduced motion, sensible touch targets.

### Reject these as final product UI
- raw checkbox/select/input as the main interaction;
- a permission button beside every toggle;
- long developer-style settings forms;
- repetitive grids of generic cards with no stronger visual idea;
- default browser/Qt scrollbars or accidental light/native scroll tracks in the dark product;
- large stacks of secondary buttons;
- raw diagnostic text in the main user flow;
- desktop forms merely squeezed onto mobile;
- generic “dashboard” composition used only because it is easy to implement;
- a UI whose only design work is colors, radius and spacing.

When there is a tradeoff, optimize for “obvious to use”, not “obvious to implement”.

## 3. Testing ownership

The executor owns the test matrix. Do not wait for the user or ChatGPT to enumerate every edge case.

For every changed feature, inspect applicability of at least:
- happy path;
- first run/bootstrap;
- zero/empty data;
- one item/event;
- high volume / rapid repeated actions;
- invalid input;
- missing permission;
- denied permission;
- permission revoked while running;
- offline;
- timeout/slow dependency;
- malformed/partial response;
- background/resume;
- process kill/restart;
- reboot/login boundary where relevant;
- rotation/window resize;
- IME/system bars/cutouts on Android;
- duplicate/concurrent action;
- interruption/cancellation;
- stale persisted state;
- previous-version data;
- upgrade;
- rollback/recovery;
- unsupported platform/session;
- small and large viewport;
- long/Unicode/unusual labels and data;
- boundary times/dates where time-based behavior exists;
- recovery after the failure condition disappears.

Add further cases discovered from the real implementation.

## 4. Evidence hierarchy

A green CI run does not by itself prove a feature.

Use and distinguish:
1. unit tests — pure deterministic logic;
2. storage/integration tests — persistence and migrations;
3. native integration tests — real OS API behavior;
4. UI tests — actual interaction and state restoration;
5. end-to-end tests — real source -> storage -> bridge/service -> rendered result;
6. upgrade/regression tests — compatibility and retained data;
7. physical-device/manual validation — only when automation cannot realistically prove it.

Mocks are allowed for narrow failure injection but must not be used as evidence that an OS integration works.

Examples:
- pre-seeding SQLite does not prove a collector;
- direct emit() does not prove X11 sampling;
- opening the Activity page does not prove UsageEvents collection;
- a mocked Supabase response does not prove Android networking;
- a compiled APK does not prove emulator or physical-device behavior.

## 5. Regression policy

Whenever practical, every bug fix must add a regression test that fails before the fix.

For each changed area:
- parsers/state machines get direct deterministic tests;
- storage changes get migration/persistence tests;
- lifecycle changes get restart/resume/recreation tests;
- sync/background work gets offline/retry/restart tests;
- destructive actions get cancellation/failure tests;
- OS integrations get real integration/e2e coverage where CI allows it;
- material UI changes get screenshot evidence in compact/mobile and at least one non-happy state;
- release-sensitive changes preserve signing/upgrade/data-retention verification.

Do not chase a meaningless percentage target. Cover risk.

## 6. Activity-specific current mission

Current P0-A is to repair and prove real Activity collection; do not make the P2 redesign a prerequisite. P0-B contains immediate cloud privacy risks and P0-C covers connection/release verification. TASKS.md defines the full priority contract.

### Android proof path
Usage Access -> real UsageEvents -> Collector -> SQLite -> native bridge -> activity.js -> rendered timeline.

The existing tests that only open Activity or pre-seed SQLite are insufficient.

Cover:
- collection just enabled;
- no event yet;
- real foreground transition;
- rapid app switching;
- long session;
- screen lock/unlock;
- permission denied/granted/revoked;
- collect on open;
- deferred/background collection;
- JobScheduler state;
- app restart/background/resume;
- rotation while Activity is visible;
- local collection while Supabase is unavailable;
- duplicate events;
- midnight/day boundary;
- upgrade/data retention.

Expose safe diagnostics for: usage-access state, enabled state, cursor, last collect attempt/success, boundaries seen, last package/app, local event count, last event timestamp and scheduler/error state.

### Linux proof path
real active X11 window -> xprintidle/xprop/WM_CLASS -> agent -> SQLite -> Qt bridge -> rendered timeline.

The existing direct emit() tests are insufficient.

Cover:
- agent starts after install/app launch/update/login;
- agent missing/crashed/stale;
- duplicate agent;
- DISPLAY missing/invalid;
- xprop unavailable/failing;
- xprintidle unavailable/failing;
- idle threshold;
- lock/unlock when available;
- active-window change;
- X11 session;
- Wayland detection and honest unsupported/degraded state unless a robust source is implemented;
- local collection while Supabase is unavailable;
- restart/update persistence.

Expose safe diagnostics for: agent heartbeat/PID, XDG_SESSION_TYPE, DISPLAY, required tool availability, last sample, last sample error, active WM_CLASS, local event count and last event timestamp.

### Activity UX (P2, after P0-A)
Do not retain the current checkbox + separate permission button form.

Desired interaction model:
- enabling Apps checks Usage Access and, if missing, launches the relevant OS flow; returning to LinkUp automatically rechecks state;
- enabling Location does the same for location permissions;
- cloud/background-session details are behind a secondary “cloud/advanced” surface;
- only show the background-session password when cloud Activity sync is enabled and main Supabase is connected;
- diagnostics live behind a compact inspector icon/sheet;
- the timeline is the visual hero;
- device/app/overlap information is understandable at a glance;
- overlapping phone/laptop use is visually encoded, not only listed as a number;
- local and cloud health are shown separately.

Before finalizing this Activity redesign, internally explore at least two materially different compositions and choose the stronger one.

## 7. PR requirements

Before opening a PR, include in its description:
- the actual user problem;
- root cause(s) found;
- chosen interaction/design concept;
- why it is better than the obvious form/dashboard solution;
- alternatives considered internally at a high level;
- explicit test matrix;
- automated evidence by layer;
- CI links;
- screenshots for material UI changes, including mobile/compact and at least one error/empty/permission state;
- what was tested on emulator/Xvfb;
- what still requires a physical phone or real Linux session;
- any remaining unsupported state.

Do not mark an item complete because code exists. Mark it complete only when the claimed behavior is demonstrated at the appropriate evidence level.

## 8. Release discipline

Do not publish a production release automatically after a feature PR unless the user explicitly requests it.

A PR is not ready if:
- it technically works but the UX is generic;
- it looks impressive but failure handling is weak;
- CI is green but the real integration path was not exercised;
- important edge cases were omitted without explanation;
- screenshots reveal default/native controls, bad scrollbars, clipping or generic form layouts.

## 9. Security and keyboard execution

Implement the owner-selected cryptographic suite, trust model and cloud-sync gate in TASKS.md, not a different protocol. Produce SECURITY_PROTOCOL.md and interoperability vectors before integration. Distinguish local plaintext history (allowed) from cloud plaintext user content (prohibited). Verify payloads before TLS and at rest in test cloud storage, not merely encrypted packet captures. Use synthetic fixtures only. Do not start automatic cloud clipboard sync until its security dependencies pass. The local HeliBoard IME may proceed in parallel, with no INTERNET permission and strictly offline on-device dictation derived from the accessible Ask Fragments source. If that source is unavailable, report it and proceed with independent tasks; do not invent an equivalent or introduce online speech. Keep all claims tied to evidence and owner approval.
