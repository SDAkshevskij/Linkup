# Roadmap

The items below are proposed sequencing, not promises or approval to implement unrelated features.

1. Stabilize Android CI: compile, lint, emulator smoke tests, download a tested APK. Keep diagnostics accessible and document failures.
2. Establish repeatable APK signing with a persistent owner-controlled key stored outside the public repo; validate update installation without deleting data. Check Linux package and runtime.
3. Validate Transfer across real Android/Linux devices with a dedicated test Supabase/cloud account. Improve durable background sync and OAuth renewal; test offline recovery and deletion conflicts.
4. Improve Activity accuracy: app transitions, idle/lock handling, day boundaries, session grouping and overlaps, permission changes and background reliability; verify location accuracy and energy use. Improve timeline and location experience.
5. Security and reliability: protect local secrets, plan end-to-end encryption, retention/backup controls and trusted device model.
6. Later optional modules (not yet committed): device status, notifications, focus and automation, safe remote actions with explicit permission. Consider Shizuku only as an opt-in extension, not a baseline dependency.
