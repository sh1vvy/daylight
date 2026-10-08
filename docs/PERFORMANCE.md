# Daylight 0.1.9 performance changes

This pass reduces repeated network, disk, image-analysis and layout work while keeping the existing playback features and visual effects.

| Cost found | Change | Limits and behavior |
| --- | --- | --- |
| Opening a playlist and asking whether it is editable fetched the same browse response separately; revisits repeated all page requests. | Share concurrent requests and retain parsed successful pages briefly. Album metadata and its authoritative track listing remain separate and are joined when needed. | 60-second freshness from completion; up to 8 first pages/1,200 rows and 16 continuation pages/2,800 rows. Failures are retried. Account, channel, language and continuation identity partition entries. Refresh and playlist/library writes invalidate them. |
| Opening an online playlist first verified every downloaded file. Closing a page left its pagination request running. | Start saved-file checks and online loading together; cancel redundant file checks once online songs arrive. Tie loading, Spotify matching and continuation work to the exact page instance. | Failed or empty online results retain the downloaded copy. Cancellation preserves other readers of a shared request. Repeated continuation tokens stop loops; an empty intermediate page can still continue. |
| Each APK update cleared audio and artwork caches, causing fresh downloads and decodes. | Keep compatible caches through ordinary version updates. | Audio schema migration, source-specific cleanup, bounded eviction and manual cache clearing still apply. |
| Two sizes of one HTTP cover could download simultaneously. SMB covers lacked encoded disk reuse. Player palettes and blurred backgrounds could repeat their analysis. | Gate cache-enabled HTTP requests per encoded-image key, cache SMB bytes on disk and share in-flight reads, and coalesce derived artwork analysis. | Different covers remain concurrent. Cancelled producers cannot overwrite replacements. Private-server credentials partition image, palette and extracted-art keys. Cache policies remain respected. |
| Remote embedded-art lookup scanned the entire cover directory and retained an unbounded URI map. | Run file lookup/extraction on IO, check six possible filenames directly, and retain a bounded recent-result cache. | 256 URI entries; missing covers retry after 30 seconds. Deleted cached files can be extracted again. |
| Player gradient color changes and playing bars read animation values during composition/layout. Repeated recompositions rebuilt sorted lists, columns and duration totals. | Read those animation values during drawing; memoize list transformations; give lazy rows stable identities and reusable content types. Skip swipe-state allocation on rows without swipe actions. | Animations, haptics, sorting, filtering, duplicate rows and swipe-to-queue remain available. |
| A detail header's canvas video decoded after it had scrolled out of view. Notification lyrics polled every 500 ms, including after the final lyric. | Pause invisible header canvases and resume on return. Schedule notification captions at lyric boundaries using a precomputed binary-search timeline. | Notification seeks, silence skips, playback-speed changes and resume reschedule immediately. Paused/missing/final lyrics need no polling. Lyric provider changes reload the timeline; stale provider replies are discarded. |

The bitmap cache retains the existing 20% heap allowance with a 64 MiB ceiling. Encoded Coil and embedded-art disk caches remain capped at 100 MiB each. Derived caches retain 128 palette seeds, 64 mesh palettes, 64 small artwork meshes and 8 blurred 128-pixel images. Pending request entries disappear after their readers finish.

## Validation

Regression tests cover request reuse, expiry, bounded eviction, account/language isolation, refresh/write invalidation, concurrent-reader cancellation, stale producers, offline fallback, image cache policies, credential changes, stable keys, and notification timing after seeks and speed changes.

The final local checks covered 1,103 tests: 1,102 passed, and the optional Genius live-provider check was skipped. The development APK built successfully. CI now runs the shared-library tests alongside the Android tests.

Controlled fixtures demonstrate the avoided work:

- A three-page playlist with a 300 ms loader per page makes three requests on a cold load: first rows at 300 ms and the full listing at 900 ms. A revisit within freshness makes no additional requests or simulated wait.
- A three-minute lyric fixture with a line every ten seconds schedules 34 wakeups instead of the previous 360 half-second wakeups. This is a scheduler comparison, not a measured battery or whole-app CPU improvement.
- Simultaneous requests for one SMB cover share one read; a later request reads the encoded disk entry. Different covers still run concurrently.

An Android 35 emulator upgrade retained all 283 existing image-cache files and reopened the same 100-track public playlist with its correct duration. Ascending and descending sorting retained the expected rows.

On the final APK, a second visit to the same playlist starts at the top with no filter. Back restores the previous visit's scroll position, search field and query. Playlist filtering, artwork-colored player and lyric screens, local playback and embedded multilingual lyrics were checked in the emulator.

Two warmed scroll traces used the same playlist and drag sequence. Android's built-in median/90th-percentile frame times were 44/61 ms before the update, 48/65 ms in the first optimized trace, and 69/109 ms in a second optimized trace. This did **not** demonstrate an overall scrolling-speed improvement. The software GPU, development build and variable host load make these timings unsuitable for a physical-device FPS claim. UI load observations also include substantial automation polling overhead, so they cannot establish exact network latency. Battery savings have not been measured.
