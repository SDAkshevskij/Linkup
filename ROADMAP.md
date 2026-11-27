# Roadmap

The items below are proposed sequencing, not promises or approval to implement unrelated features.

1. Android CI baseline achieved (compile, lint, emulator smoke tests and tested APK). Keep diagnostics accessible.
2. **P0 — user priority:** persistent owner-controlled Android signing, versioned GitHub Releases and in-app updates on Android and Linux Mint without data loss. Verify upgrade across two builds, handle one-time debug migration safely. Include Android cloud connection feedback and diagnosis, responsive mobile layout and safe areas, orientation/lifecycle state preservation, and dark-theme scrollbars in the first update; defer unrelated new modules.
3. Validate Transfer across real Android/Linux devices with a dedicated test Supabase/cloud account. Improve durable background sync and OAuth renewal; test offline recovery and deletion conflicts.
4. **P0 now:** prove and repair real Activity collection on physical Android and Linux Mint, add collector health diagnostics and true end-to-end tests, then redesign Activity permission/settings UX and timeline according to the product-design rules in AGENTS.md. Only after real events work should accuracy/overlap/location refinements continue.
5. Security and reliability: protect local secrets, plan end-to-end encryption, retention/backup controls and trusted device model.
6. Later optional modules (not yet committed): device status, notifications, focus and automation, safe remote actions with explicit permission. Consider Shizuku only as an opt-in extension, not a baseline dependency.
