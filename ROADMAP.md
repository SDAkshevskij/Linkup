# Roadmap

TASKS.md is the authoritative priority/dependency contract; this file is a summary, not additional implementation scope.

- Completed baseline: Android compile/lint/emulator, update and UI regression evidence; owner production signing and real-device validation are still pending.
- P0: immediate privacy containment, repair real Android/Linux X11 Activity collection and verify basic cloud connectivity/release continuity with synthetic test data.
- P1: LinkUp Security v1 (S1–S5), the common E2EE channel for all user payloads, and local HeliBoard-based Android IME with offline on-device speech from Ask Fragments. Local history encryption and legacy migration are explicitly out of scope. Independent local IME work may proceed in parallel.
- P2: automatic cross-device clipboard, background Transfer and OAuth improvements after applicable E2EE gates; Activity redesign and device UX/package validation.
- P3: investigate Wayland; support only after a reliable implementation is verified.
- Other possible modules (notifications, focus, automation, explicit safe remote actions) are ideas only, not current assignments.

Do not upload user content without verified E2EE. Keep local-only operation available, and do not publish production releases without owner approval.
