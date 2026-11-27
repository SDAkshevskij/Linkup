# LinkUp — project context

## Confirmed product direction
LinkUp is one connected personal application across Android and Linux Mint, with platform-specific UI and capabilities as needed. Devices must remain usable independently, even when the other is off. A web-only PWA is not the final product; system integration and background agents are required.

- Supabase is the chosen server for structured data, auth and synchronization; use Yandex Disk or Google Drive for heavy files.
- Transfer: a shared stream for text, links and arbitrary files, with history, search/filter, copy/download, individual deletion, and cleanup by age (day/week/month etc.). Preserve offline operation and queued changes.
- Activity: track the user's own device activity, foreground applications, usage duration, session history, device source and location history where permission allows. Combine both platforms into one day view; use colored time segments and separate device tracks, with drill-down into sessions.
- Design: bold, compact, minimal explanatory labels, adapted to desktop/mobile; dark theme with lime accents is the existing implementation, not an immutable requirement.
- Prefer free infrastructure during personal prototyping. Root is not required for baseline functionality.

## Development approach
Version 0.1.0 was a web prototype. Version 0.2.0 uses installed native shells/agents with a bundled local HTML/CSS/JS UI. This is not a hosted website. Flutter was proposed but not adopted due to toolchain availability. Do not migrate without evaluating preservation of functionality and discussing the decision.

Stable updates use GitHub Releases with a monotonic version code. Android uses the owner-controlled signing key and in-place installation. Linux Mint switches per-user program versions and retains its existing data paths. Release CI uses only synthetic test data and temporary test keys until the owner enables protected release signing.

GitHub is the common source of truth for ChatGPT and Codex. The repository is intentionally public at the owner's direction; publish code and sanitized project decisions only. Do not copy private chats, personal records or credentials.
