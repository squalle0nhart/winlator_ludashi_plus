# Steam update

Source: [Bannerlator 8b1750e](https://github.com/The412Banner/Bannerlator/tree/8b1750e7863a01db4837fd2c0a54a7242dc01ef7),
the main revision checked on 2026-09-08 (3.0.8 preparation).

This ports fixes to the existing JavaSteam integration:

- Show Lossless Scaling in the library and exclude its stale duplicate depot
  993092, requiring the maintained depot 993091 before starting its download.
- Filter depot size estimates to Windows, English/shared content, and public
  manifests; replace stale depot metadata during library sync.
- Sum cumulative progress across depots, keep counters monotonic when callbacks
  arrive out of order, preserve the resume floor, and persist corrected totals.
- Wait for a live session before downloading and suppress duplicate token logons.
- Hold CPU/Wi-Fi locks during downloads and keep active downloads running when
  the task is dismissed.
- Release download controls on success, failure, pause, and cancel; clear completed
  download rows; reconnect the detail page to active controls; recover interrupted
  rows as paused. Partial files are kept when cancelling, allowing a later retry.
- Register the bundled BC provider after the platform providers, preserving the
  platform's default TLS provider.

The lifecycle adaptation also handles JavaSteam 1.8.0 leaving its completion future
pending after `close()` or an error callback. A bounded wait checks stop requests and
callback failures; teardown precedes terminal UI events.

This is a fixes port, not full Bannerlator feature parity. The Rust Steam engine,
SteamLite/real Steam and EA launching, social/chat, cloud saves, branch/DLC pickers,
media UI, and automatic incomplete-depot recovery are outside this port. Existing
Ludashi launch shortcuts and JavaSteam dependencies are retained.

Run `./gradlew assembleDebug testDebugUnitTest`. `SteamDownloadTest` checks progress,
pending-future stop/error handling, and depot/library selection. Steam sign-in,
real CDN transfers, screen-off downloads, and game launching need device validation
with an owned game. Refresh the Steam library once after updating to replace old
depot metadata.
