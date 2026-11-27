# LinkUp product quality bar

This file is a binding acceptance contract for coding agents. Functional correctness and visual quality are both release requirements. A feature is not done merely because the happy path works.

## 1. Product-design standard

LinkUp must feel authored, distinctive and deliberate. It must not look like a developer panel, a settings dump, a generic CRUD dashboard, or a web form wrapped in a native shell.

The executor is expected to do design work, not only CSS cleanup. When a straightforward implementation would produce a boring or obvious UI, actively explore stronger interaction and composition ideas before coding. The target is a design that is simultaneously:
- immediately understandable without instructions;
- fast to operate;
- visually memorable and recognizably LinkUp;
- compact without becoming cramped;
- responsive to desktop and phone rather than merely shrinking;
- accessible and robust under real platform constraints.

Do not optimize for “obvious to implement”. Optimize for “obvious to use”.

### Required design behavior
- Establish a visual hierarchy before writing the final component markup.
- Prefer one strong focal object/action per surface. For Activity, the timeline is the hero.
- Use progressive disclosure: frequent actions stay visible; advanced, destructive and diagnostic actions live in deliberate secondary surfaces.
- Integrate permissions and state changes into the control that owns the feature. Avoid button-per-permission/button-per-field layouts.
- Replace raw checkbox/select/input patterns with designed components when they are part of the primary product experience.
- Avoid repetitive card grids when a stronger spatial composition can communicate the same information.
- Use motion/transition only when it clarifies state, continuity or hierarchy; never as decoration that slows interaction.
- Preserve touch targets, contrast, keyboard navigation, focus visibility and reduced-motion behavior.
- Do not expose default browser/Qt scrollbars, default dialogs, accidental white tracks, raw system-looking controls, horizontal overflow, clipped controls or desktop forms squeezed into mobile.
- Empty, loading, offline, permission-denied, partial-data, error and recovery states must receive the same design attention as the happy path.

Before finalizing a user-facing redesign, generate at least two materially different interaction/composition concepts internally and choose the stronger one. Do not present multiple half-finished concepts in production.

## 2. Adversarial testing standard

The executor owns the test matrix. The user should not need to enumerate every edge case.

For every changed feature, identify:
1. happy path;
2. first-run/bootstrap;
3. empty state;
4. denied/missing permission;
5. permission revoked while running;
6. offline/no server;
7. slow server/timeout;
8. malformed/partial response where applicable;
9. process/app restart;
10. background/resume;
11. rotation/window resize;
12. concurrent/duplicate action;
13. interrupted operation;
14. stale persisted state from a previous version;
15. upgrade from the previous release;
16. incompatible/unsupported platform state;
17. very small and large viewport;
18. keyboard/IME and system bars on mobile;
19. long/Unicode/unusual user data;
20. recovery after the failure is removed.

Not every item applies to every feature, but the PR must state which ones apply and how each is covered.

### Test layers
Use the lowest useful layer and also test critical paths end-to-end:
- pure logic/unit tests for deterministic transformations;
- storage/integration tests for persistence and migrations;
- native integration tests for OS APIs;
- UI tests for actual interaction/state restoration;
- end-to-end tests for the real source -> storage -> bridge -> rendered result path;
- upgrade/regression tests for data retention;
- screenshots for major visual surfaces/states.

Mocks are allowed for narrow failure injection but must not be used as proof that an OS integration works. Pre-seeding SQLite does not prove a collector. Opening a page does not prove data collection. A green build does not prove the product behavior claimed in the PR.

## 3. Coverage expectations

Do not chase a meaningless percentage target. Instead require risk coverage:
- every bug fix gets a regression test that fails before the fix when practical;
- every parser/state machine/storage migration gets direct tests;
- every OS integration gets at least one real integration/e2e path in CI where technically possible;
- every important user flow gets at least one UI/e2e test;
- every destructive action gets cancellation and failure coverage;
- every background/sync feature gets restart/offline/retry coverage;
- every release-affecting change keeps upgrade/signature/data-retention verification.

If a scenario cannot be automated, record it explicitly as physical-device/manual validation. Never silently omit it.

## 4. PR acceptance checklist

A PR is not ready merely because CI is green. Before requesting review:
- describe the user problem, not only implementation details;
- state the visual/interaction concept and why it is better than the obvious form-based solution;
- list the test matrix and evidence;
- distinguish unit, integration, emulator and physical-device evidence;
- attach screenshots for material UI changes, including compact/mobile states and at least one non-happy state;
- confirm no new raw/default scrollbars or platform controls leaked into the product surface;
- confirm local functionality still works when cloud services fail where local-first behavior is expected;
- update TASKS.md, STATUS.md and CHANGELOG.md truthfully.

A technically functional but visually generic implementation should be revised before merge. A beautiful implementation with weak failure handling should also be revised before merge.
