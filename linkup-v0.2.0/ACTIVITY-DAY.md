# Activity day navigation and time scale

## Scope

P0-A includes local day boundaries, background/resume and observable collection through the timeline. The remaining owner-device, live Supabase and release gates stay open. PR #6 handles S1 separately.

The existing view captures the calendar date at module load, derives tomorrow by moving an already-normalized midnight, and leaves the previous day's figures visible while a fresh SQLite request is pending. A day that starts at 01:00 because the clock jumps at midnight therefore includes an extra hour from the next day. Equal-width 24-hour labels also misrepresent shorter and longer days.

## Open Source First

| Candidate and source | Decision |
| --- | --- |
| [date-fns v4.1.0](https://github.com/date-fns/date-fns/tree/313b902b9a72c64501074db9bc2b9897d2db5140), MIT. Reviewed `src/startOfDay/index.ts`, its tests and `src/addDays/index.ts`. | Reference only. Compute the two local starts independently with existing JavaScript Date to avoid carrying a normalized 01:00 into tomorrow. A package would add a dependency to both bundled shells for two operations. |
| [d3-time v3.1.0](https://github.com/d3/d3-time/tree/d66bba6f83801b2f3a3e6440dba8df3357669c73), ISC. Reviewed `src/hour.js`, tests and its d3-array dependency. | Reference only. Use elapsed instants and local clock labels on the shared timeline. The existing DOM/CSS renderer needs neither a chart runtime nor upstream code. |
| [ActivityWatch aw-webui](https://github.com/ActivityWatch/aw-webui/tree/b5bb8c7a69591860f74f25870187630107360465), MPL-2.0. Reviewed `InputTimeInterval.vue` and `InputTimeIntervalControls.test.ts`. | Reference only. Retain previous/next and a return-to-today action. A range form, Vue and Moment do not fit a single-day, offline LinkUp screen. No upstream files or assets are imported. |

## Design

Considered a seven-day ribbon with separate calendar and a compact day navigator attached to the timeline. Chose the navigator to keep the colored tracks prominent at 320 dp and keep the same previous/next targets on desktop and phone. The selected day occupies the center, and archived days have a direct return to today. Calendar entry lives in a deliberate secondary sheet. Existing dark surface, lime live state and colored application bars carry the hierarchy. Tabular times align the ruler, while everyday Russian labels identify the day and recovery actions.

## Skills applied during planning

- **systematic-debugging and test-driven-development.** Trace date input through the query and the render. Reproduce a clock jump at midnight, a resumed live day and stale figures from a delayed query before editing production code.
- **property-based-testing.** Compare generated day ranges with an independently constructed local calendar oracle across several time zones. Check that hourly cells partition the full elapsed range and concrete gap/fold and skipped-day cases remain fixed.
- **frontend-design and impeccable-text-review.** Compare date ribbon and day navigator. Render before/after and check compact, historical empty and desktop states. Keep the native calendar entry secondary.
- **cross-app-tests-with-uiautomator.** Extend the existing emulator proof based on a real Settings transition, checking that moving to an empty day and returning to today preserves the collected row.
- **differential-review and verification-before-completion.** Trace the local bridge, request identity and rendering; verify the final commit, screenshots and physical limitations.

## Verification matrix

| Scenario | Evidence |
| --- | --- |
| Open view across midnight, switch dates rapidly and request an archive during a delayed Qt-style callback | Three baseline RED failures followed by green regression checks. An obsolete response is delivered after returning to today. |
| Stay on an explicit past day, retry a failed or unanswered request and change time zone with a same-label request in flight | Controller tests cover duplicate background refresh coalescing, a manual retry regression, superseded requests, retained same-day data and recovery. |
| Clock gap at midnight, repeated or missing hours, a half-hour transition, a skipped calendar date and leap day | Named examples and 900 generated days/event placements in five time zones. Oracle uses numeric local Date construction and enumerated quarter-hour occupancy. The seed is recorded in the test. |
| Empty archived day and return to today after real OS use | Native Android Settings/UsageEvents and Linux X11 fixture paths, SQLite, existing bridges and visible colored marks. Android uses an accessibility-window package wait around the real Settings transition. Local Mint 22.2 Xvfb passed all 12 tests. |
| Small viewport, permission revoked and desktop | The Android suite captures local history, empty archive, calendar sheet and revoked access at 320 dp. The Qt fixture captures the same real row after the day round trip. |
| History preservation and platform limits | Existing signed Android and Qt upgrade tests remain in CI. Physical clock/time-zone changes, regular Mint login and real cloud credentials require the owner checklist. Wayland collection remains unsupported. |

## Differential review

Reviewed the changed day parser and clipping boundary from Android Collector and Linux agent SQLite through `activity`, the shared renderer and the existing map action. Both native queries still take numeric bounds; the renderer applies a half-open interval to exclude the adjacent date. A superseded request cannot publish rows under another date. The date inputs do not create bridge actions, and application/device names are still escaped in HTML and attribute contexts. A regression asserts the escaped markup using synthetic names. Day navigation adds no network route or persistence. The existing user-triggered map handoff is unchanged. This review covers the diff and its immediate call sites.
