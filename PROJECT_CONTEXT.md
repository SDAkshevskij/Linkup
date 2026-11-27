# LinkUp — project context

## Confirmed product direction
LinkUp is one connected personal application across Android and Linux Mint, with platform-specific UI and capabilities as needed. Devices must remain usable independently, even when the other is off. A web-only PWA is not the final product; system integration and background agents are required.

- Supabase is the chosen server for structured data, auth and synchronization; use Yandex Disk or Google Drive for heavy files.
- Transfer: a shared stream for text, links and arbitrary files, with history, search/filter, copy/download, individual deletion, and cleanup by age (day/week/month etc.). Preserve offline operation and queued changes.
- Activity: track the user's own device activity, foreground applications, usage duration, session history, device source and location history where permission allows. Combine both platforms into one day view; use colored time segments and separate device tracks, with drill-down into sessions.
- Design: bold, memorable and intentionally unconventional while remaining obvious to use. Do not default to generic forms, dashboard card grids or developer/admin UI simply because they are easy to implement. The executor is expected to invent stronger interaction/composition ideas and test them against usability. Dark/lime is the current identity, not a license to ship basic components. PRODUCT_QUALITY.md defines the acceptance bar.
- Prefer free infrastructure during personal prototyping. Root is not required for baseline functionality.

## Development approach
Version 0.1.0 was a web prototype. Version 0.2.0 uses installed native shells/agents with a bundled local HTML/CSS/JS UI. This is not a hosted website. Flutter was proposed but not adopted due to toolchain availability. Do not migrate without evaluating preservation of functionality and discussing the decision.

Stable updates use GitHub Releases with a monotonic version code. Android uses the owner-controlled signing key and in-place installation. Linux Mint switches per-user program versions and retains its existing data paths. Release CI uses only synthetic test data and temporary test keys until the owner enables protected release signing.

GitHub is the common source of truth for ChatGPT and Codex. The repository is intentionally public at the owner's direction; publish code and sanitized project decisions only. Do not copy private chats, personal records or credentials.

## Current approved planning decisions (not yet implemented)

- One mandatory end-to-end encrypted data channel for all synchronized content, including cloud-stored file bytes and sensitive metadata. Fixed libsodium v1 suite and trust model are specified in TASKS.md S1–S5. Ordinary local history may remain plaintext under OS access controls; no legacy migration. Private keys and tokens require protected storage.
- Use actual HeliBoard sources for a separate offline Android IME without INTERNET permission. Reuse Ask Fragments local microphone path; no Codex/cloud/desktop speech. Automatic clipboard sync is dependent on verified E2EE; only deliberately copied clipboard items can be shared, never ordinary typing or voice capture.
- TASKS.md controls priorities; CODEX_PROMPT.md is a compact launch prompt. These are planning commitments, not completed features.
