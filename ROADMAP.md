# Roadmap

The items below are proposed sequencing, not promises or approval to implement unrelated features.

1. Android CI baseline achieved (compile, lint, emulator smoke tests and tested APK). Keep diagnostics accessible.
2. **P0 — user priority:** persistent owner-controlled Android signing, versioned GitHub Releases and in-app updates on Android and Linux Mint without data loss. Verify upgrade across two builds, handle one-time debug migration safely. Include Android cloud connection feedback and diagnosis in the first update; defer unrelated new modules.
3. Validate Transfer across real Android/Linux devices with a dedicated test Supabase/cloud account. Improve durable background sync and OAuth renewal; test offline recovery and deletion conflicts.
4. Improve Activity accuracy: app transitions, idle/lock handling, day boundaries, session grouping and overlaps, permission changes and background reliability; verify location accuracy and energy use. Improve timeline and location experience.
5. Security and reliability: protect local secrets, plan end-to-end encryption, retention/backup controls and trusted device model.
6. Later optional modules (not yet committed): device status, notifications, focus and automation, safe remote actions with explicit permission. Consider Shizuku only as an opt-in extension, not a baseline dependency.
