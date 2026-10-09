# Daylight performance notes

## Dev.6: playback and lyric warming

This pass moves source lookup and small audio openings ahead of track changes while keeping disk use, background concurrency and analysis bounded.

| Area | Behavior and budget |
| --- | --- |
| Audio quality | Wi-Fi offers Low, High, Lossless up to 24-bit / 48 kHz, and Hi-Res Lossless up to 24-bit / 192 kHz. Lossless requires an unmetered connection; cellular stays Low/High. Lossless tier/connection changes refresh eligible noncurrent queue entries; the currently opened file is preserved. |
| Source lookup | Recording-, endpoint- and quality-specific lookups share concurrent work. Up to 128 results are memoized, with ten-minute verified results and thirty-second stable misses. Network failures are retried; the five-second budget includes waiting for a shared lookup. |
| Audio disk cache | Normal playback defaults to 512 MiB; either lossless tier requires at least 1 GiB. The selected ceiling remains enforced by LRU eviction, including preferred entries. FLAC uses persistent recording/header-fingerprint keys separate from YouTube; lossless fallback keys identify the YouTube rendition. |
| Queue warming | Follow actual shuffle/repeat order and warm at most five unique upcoming entries sequentially. Opening budgets are 256 KiB normal, 2 MiB Lossless and 4 MiB for the Hi-Res selection. After the openings, Wi-Fi can fill more of the immediate next track, capped at 64 MiB or one sixteenth of the disk budget. No whole-playlist download is started. |
| Playback priority | Warm only after at least eight seconds of the current track are buffered, or its end is already buffered. The current track, previous two played tracks and next five openings receive eviction priority. Pause, skips, queue edits and eligibility changes cancel stale work. Cache locks prevent background reads from downloading competing uncached bytes. |
| Lyrics | With synced lyrics enabled, the service warms the next three songs on unmetered connections, one song and at most two selected providers at a time. The current song and previous three played songs stay in the retained window. Only successful results are cached, up to 48 source results and 2 MiB of estimated result weight. Missing lyrics and network failures never become lasting negative cache entries. |
| AutoMix | Hi-Res selection or actual Hi-Res playback blocks activation and background AutoMix analysis. Ordinary audio and Lossless remain eligible. The saved AutoMix choice is preserved; the settings control shows its current availability. |
| Main player | Show static quality labels only for decoder-confirmed Lossless/Hi-Res Lossless. Normal audio has no label. Confirmed missing lyrics disable the lyrics control; unresolved availability remains retryable. |

Prepared playback and queue warming select the same file before choosing its byte-cache key. A warm decision cannot be adopted after its quality or eligibility changes, while a committed decision stays fixed across seeks. Local files, saved downloads, video-origin items, original-version pins, Cast and Jam synchronization retain their established paths. See [lossless implementation notes](LOSSLESS_BETA.md) for matching and format verification.

## Earlier playlist and artwork improvements

These optimizations remain in place:

| Cost | Change and bounds |
| --- | --- |
| Repeated playlist pages | Share concurrent browse requests and briefly retain successful parsed pages. Freshness is sixty seconds from completion, with up to eight first pages/1,200 rows and sixteen continuation pages/2,800 rows. Account, channel, language and continuation identity partition entries. Refresh and playlist/library writes invalidate them. |
| Playlist opening and pagination | Run saved-file checks beside online loading, cancel redundant checks after online songs arrive, and tie pagination/Spotify matching to the exact page instance. Failed or empty online results retain downloaded copies. Repeated continuation tokens stop loops. |
| Repeated artwork requests | Share cache-enabled HTTP reads for one encoded-image key; cache SMB image bytes and share in-flight reads. Bound embedded-art lookup at 256 URI results, retrying missing covers after thirty seconds. Credentials partition private-server image and derived-art keys. |
| Repeated image analysis/layout | Coalesce palettes and blurred backgrounds, reuse list transformations and stable row identities, and draw continuously changing gradients/bars without unnecessary layout work. Preserve animations, haptics, filtering and duplicate rows. |
| Notification lyrics | Schedule captions at lyric boundaries using a binary-search timeline. Seeks, silence skips, speed changes and resume reschedule immediately. Paused, missing and final lyrics need no polling; stale provider replies are discarded. |
| App updates | Keep compatible audio/artwork caches across ordinary APK updates. Schema migration, bounded eviction and manual clearing still apply. |

The bitmap cache keeps its 20% heap allowance with a 64 MiB ceiling. Encoded Coil and embedded-art disk caches are capped at 100 MiB each. Derived caches retain up to 128 palette seeds, 64 mesh palettes, 64 small artwork meshes and eight blurred 128-pixel images. Pending request entries are removed when their readers finish.

## Verification and measurement limits

Regression coverage checks migration, format ceilings, distinct byte keys, bounded warming/eviction, lookup reuse and cancellation, lyric positive-result limits, settings/provider identity, stale replies and AutoMix gating. Playlist/artwork coverage also checks account isolation, refresh invalidation, concurrent-reader cancellation, cache policies and offline fallback.

Earlier controlled fixtures showed a three-page playlist with a 300 ms loader per page reusing all three successful pages during freshness, and a three-minute lyric fixture using 34 boundary wakeups instead of 360 half-second polls. An Android 35 upgrade retained 283 image-cache files and reopened a 100-track playlist with the correct duration.

Those fixtures demonstrate avoided work, not measured physical-device battery savings. Earlier software-GPU emulator scroll traces did not establish a frame-rate improvement. Device measurements are still needed before claiming a specific startup time, FPS gain or battery reduction.
