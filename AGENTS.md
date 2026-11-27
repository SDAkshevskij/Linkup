# LinkUp — instructions for coding agents

Read PROJECT_CONTEXT.md, ARCHITECTURE.md, ROADMAP.md, TASKS.md, STATUS.md and CHANGELOG.md before changing code. Inspect the current branch and latest GitHub Actions results; documentation is a handoff, not proof of current behavior. User requests take priority over proposed roadmap items.

- Current project directory: `linkup-v0.2.0/`. Active CI: root `.github/workflows/android.yml`. Nested workflow is an archival duplicate; do not use it as the active workflow.
- Preserve Android and Linux Mint support and existing Transfer and Activity functionality. Do not silently replace native agents with a web-only app.
- Keep UI compact and functional, minimal explanatory text, responsive per platform. Preserve colored daily activity timelines.
- Tracking must be opt-in on owned devices, respect OS permissions and revocation. No hidden monitoring, arbitrary remote shell, or insecure bypasses. Keep real activity data, credentials, OAuth tokens, signing keys and personal information out of this public repository and CI artifacts. Test with synthetic data.
- Prefer small, reviewable commits or PRs; never force-push or overwrite concurrent work. Check git status and latest main before starting.
- Run relevant tests (commands in ARCHITECTURE.md), build and lint Android changes, and use emulator tests when available. Fix root causes rather than disabling checks. Never claim tests passed without evidence. Distinguish compiled APK from emulator-tested APK and real-device validation.
- After each substantial task update TASKS.md, STATUS.md and CHANGELOG.md with actual changes, remaining blockers, exact test commands/results, and CI run links. Update architecture/context only when decisions change. If a new user request conflicts with backlog, record the new decision.
- For handoff between ChatGPT and Codex, write the next actionable step and commit/branch in STATUS.md. Push a branch / open PR as appropriate; changes are shared only after they reach GitHub. Do not publish secrets or unreviewed private conversation text.
