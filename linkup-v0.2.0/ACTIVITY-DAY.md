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
