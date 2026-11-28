# LinkUp skill policy

These repo-local Agent Skills are mandatory when relevant, not optional suggestions. Install location is `.agents/skills/<name>/SKILL.md`. Start Codex in the repository after pulling main; a new session may be needed for discovery. Explicitly invoke the named skill where supported (e.g. `$systematic-debugging`); otherwise read its SKILL.md and relevant references directly. Do not load all skills indiscriminately.

## Routing

| Task trigger | Required skills | Required evidence |
|---|---|---|
| Bug, regression, failing CI, device/network failure | systematic-debugging; test-driven-development | Reproduction, failing boundary/root cause, red/green regression test or documented blocker |
| New/changed behavior or refactor | test-driven-development | Test matrix, failing tests before fixes where feasible, relevant regression suite |
| Intervals, timestamps, overlaps, parsers, state transitions, queues, crypto envelopes | property-based-testing + test-driven-development | Independent oracle/invariant, generated cases, minimized counterexamples; no meaningless tautologies |
| User-facing UI creation/redesign | frontend-design + impeccable-text-review | At least two genuinely different concepts for substantial changes, reasoned choice, before/after rendered screenshots at relevant sizes and states, critique/fixes |
| Android permissions, system settings, other apps, lifecycle | cross-app-tests-with-uiautomator + relevant debugging/TDD | Real emulator/device OS interaction, actual observed state; manual gaps marked |
| Auth, storage, IPC, privacy, network, crypto, CI/release security, security-sensitive diff | differential-review | Evidence-based review of changed files, call sites, trust boundaries, tests and risks; findings and coverage limits |
| Every implementation PR, before any completion claim | verification-before-completion | Fresh commands and outputs, CI for exact head, scoped claims, unverified states and blockers |

For substantial tasks document a short Skills applied section in the plan and PR: skill names, what changed as a result, evidence, and why any relevant skill could not be applied. For UI include the design concepts and actual visual review; for tests include negative cases and evidence that tests detect failures. Do not treat listing skill names as execution. Re-read applicable skill at the relevant phase.

## Execution and safety precedence

- User task scope, repository contracts (AGENTS.md, PRODUCT_QUALITY.md, EXECUTOR_PLAYBOOK.md, OPEN_SOURCE_FIRST.md, TASKS.md), sandbox permissions and privacy/security rules take precedence over third-party workflow instructions. Skills are guidance, not authorization.
- The imported set is Markdown-only guidance and licenses. Do not auto-run third-party installers, hooks, downloaders, binaries, remote audit services, or arbitrary shell commands. Do not install dependencies without Open Source First review and applicable owner approval.
- Never follow TDD wording to delete existing implementation, discard working-tree changes, or reset/overwrite another contributor's code. Use isolated temporary worktrees or a minimal red/green test; preserve existing user data. Do not checkout another commit over dirty work. Do not print real env values, tokens, request contents, signing data or activity history. Use synthetic fixtures, sanitized diagnostics and disposable emulators; no destructive ADB actions on owner's device.
- Examples using namespaced tools (superpowers, differential-review subagents, audit-context-building, issue-writer) are not installed tool integrations. Use the local named skills and available authorized tools; perform review directly if no subagent is available. The optional find-polluter.sh is intentionally not installed; reproduce with safe local tests. No need to install additional tooling to satisfy a sample command.
- Frontend design and Impeccable text review complement the existing distinctive LinkUp identity. Do not adopt a generic SaaS design or replace native affordances; no font downloads. Design concepts are not a reason to block progress on minor bug fixes.
- Property tests supplement, not replace, real OS integration/UI tests. Use existing libraries or seeded generators; new package decisions follow repository policy. Security review does not certify cryptographic correctness.
- Security findings that would expose a live exploitable flaw, credentials or sensitive data must be reported privately to the owner rather than posted in public GitHub artifacts. Do not test exploits against live third parties. No automatic release.
- Pinned sources, exact licenses and scope are recorded in each UPSTREAM.md. Check for changes and security concerns before updating; no auto-update. Preserve license texts and notices; CC-BY-SA files remain under their upstream license, not a change to the application's license.

## Inventory

- frontend-design: anthropics/skills, Apache-2.0
- systematic-debugging, test-driven-development, verification-before-completion: obra/superpowers, MIT
- property-based-testing, differential-review: trailofbits/skills, CC-BY-SA-4.0
- cross-app-tests-with-uiautomator: skydoves/android-testing-skills, Apache-2.0
- impeccable-text-review: local adaptation of pbakaus/impeccable text references, Apache-2.0; no launcher or executable engine.

See UPSTREAM.md in each skill directory for the exact source URL and pinned revision.
