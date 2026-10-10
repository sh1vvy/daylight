# Daylight performance notes

## Canary 11: bounded Automix presentation

The new cover transition uses two cached artwork layers at the existing player
resolution. Engine progress is read in draw/layer scopes and interpolated for
60 ms on the Compose frame clock; the main player is not recomposed for every
progress tick. Animated-cover playback yields only over the brief cover dissolve,
then resumes; there are no two simultaneous motion-art decoders. The shared
3.6-second glow clock uses frame timestamps and stops outside foreground playback,
at pause and between mixes. Its halo is three small translucent capsule draws,
without an additional bitmap or dynamic blur pass.

Native tests verify stable cover bounds, contributions from both images at the
metadata handoff, reduced motion, and no root recomposition before the handoff.
A separate frame-clock check verifies the glow changes while playing and stops
requesting redraws once paused. These are regression checks; no new battery,
sustained-FPS or all-device benchmark is claimed for this candidate.

## Canary 9: scrolling on the Galaxy S22

Internal **0.2.2-canary.9 / code 28** uses the existing Dev package and signer.
The shipping candidate is `assembleDevCanary`, a non-debuggable, R8-optimized
build. Previous manual Dev/Canary APKs used the debug runtime. Android recommends
measuring Compose in optimized release builds; debugging and inspection add
cost that affects scrolling. See [Compose performance](https://developer.android.com/develop/ui/compose/performance).

| Avoided work | Implementation |
| --- | --- |
| Rebuilding detail artwork each scroll pixel | Read the header/body offsets during placement, keeping the existing sharp artwork, 48 dp masked blur, tonal fade and deferred video. |
| Recomposing shared covers each animation frame | Read the changing corner radius inside the graphics layer. The same rounded outline, bounds easing and opening/closing durations remain. |
| Repeated library transformations | Retain remote flattened lists, combined ordering and other shelves while only playback/chrome state changes. Pins, local playlists, newest-first ordering and the four-cover preview remain. |
| Broad startup compilation | Replace blanket app-package profile wildcards with 1,767 startup/hot UI methods captured from the S22's unminified runtime. Include activity/application UI lambdas; omit unrelated packages and cold methods. R8 retains/rewrites 1,508 owned rules. The merged profile is 11,136 bytes plus 1,183 bytes of metadata. |
| Returning to debug overhead in distributed builds | CI and release instructions use `assembleDevCanary` for Dev artifacts; debug remains available for development and fixture tests. Signing and release channels are unchanged. |

This follows Android's [deferred state-read guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices)
and [Baseline Profile workflow](https://developer.android.com/topic/performance/baselineprofiles/overview).
Google's [Now in Android scrolling benchmark](https://github.com/android/nowinandroid/blob/main/benchmarks/src/main/kotlin/com/google/samples/apps/nowinandroid/foryou/ScrollForYouFeedBenchmark.kt)
was also reviewed. Strong skipping already comes from the project's Kotlin
version; this pass does not claim to enable it for the first time.

### Phone measurements

Galaxy S22 **SM-S901E**, Android 16, 1080 × 2340, active 120 Hz, USB charging.
The original installed Canary 8 was captured before updating it in place.
No account, playlist, preference or cache was cleared on the phone. The saved
Liquid Glass choice, blur, artwork and theme were retained. Thermal status was
light (1); battery temperature stayed roughly 37–38 °C during the runs.

Each playlist pass performs eight alternating 500 ms vertical swipes with
120 ms pauses. The same Liked songs header and first rows are traversed, with
music paused. Android `gfxinfo` provides deadline counts and frame-time
percentiles; raw framestats are retained in the ignored candidate QA folder.

| Liked songs pass | Canary 8 missed deadlines | Packaged Canary 9 missed deadlines | 95th-percentile frame time, before → after |
| --- | --- | --- | --- |
| 1 | 21.84% (114 / 522) | 3.03% (20 / 659) | 38 → 23 ms |
| 2 | 16.03% (93 / 580) | 1.76% (12 / 680) | 34 → 18 ms |
| 3 | 14.04% (82 / 584) | 2.41% (16 / 665) | 32 → 18 ms |

Median playlist deadline misses fall from **16.03% to 2.41%** in these passes.
Home's initial pass was 9.79%; three later optimized passes were
3.93%, 4.10% and 3.70%. The Home starting shelf positions were not identical,
so treat that comparison as indicative. Fresh-install/JIT warmth, asynchronous
metadata and artwork cache warmth also vary. These measurements compare the
whole installed candidates, including debug versus optimized runtime; they do
not isolate the contribution of each code change. They are not a sustained-FPS,
Oppo Find X9, battery-life or all-device guarantee. More hardware can reuse the
same procedure.

### Repeating the checks

Build the optimized candidate, open the intended list and let its rows load.
Use `tools/measure_scroll.py --serial DEVICE --adb /path/to/adb --label RUN
--output output/qa` (one command) for three captured passes. `--pattern round-trip`
uses four downward then four upward swipes for a longer Home traversal. Match
screen position, theme, refresh rate, playback, temperature and image warmth.
The tool resets frame counters, not application data or device settings.

`tools/refresh_scroll_profile.py CAPTURE` regenerates the app profile from an
unminified ART text export. It selects owned UI startup/hot methods only,
rejects empty captures and refuses more than 4,096 rules. Library profiles merge
separately. Verify the packaged `assets/dexopt` profile and ProfileInstaller
success before comparing builds; no forced full compilation was used here.
No cache budget, animation speed, artwork quality or background worker limit
was increased for this pass.

### Regression checks

1,418 app/shared checks passed, with one optional live check skipped. Thirteen
native cases passed on the disposable Android emulator: cover motion from Home
and Library, all three themes, Glass and Reduce animation, library grid/order,
one-tap mix playback/refills, profile/search, catalogue navigation, synced lyric
motion and the update card's actions/layout in all three themes. Native fixtures
use the debug variant because they reference app internals that R8 removes;
performance data and playback/lyrics smoke checks use the optimized real-phone
APK. An initial profile-menu fixture timed out under concurrent build load and
passed separately; synthetic account residue was cleared only on the owned
emulator before the clean motion run. Skipped guard checks are not counted as
passes. The S22 account and library were never reset.

The update notice uses solid themed surfaces, an eased entrance, bounded
scrollable Markdown notes and clear primary/secondary controls. Download,
cancel, failure retry, install permission handling and dismissal during a
running download retain their existing behavior. Release polling and the
internal-canary update-check exclusion are unchanged.

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
| Repeated artwork requests | Share cache-enabled HTTP reads for one encoded-image key and coalesce in-flight reads. Bound embedded-art lookup at 256 URI results, retrying missing covers after thirty seconds. |
| Repeated image analysis/layout | Coalesce palettes and blurred backgrounds, reuse list transformations and stable row identities, and draw continuously changing gradients/bars without unnecessary layout work. Preserve animations, haptics, filtering and duplicate rows. |
| Notification lyrics | Schedule captions at lyric boundaries using a binary-search timeline. Seeks, silence skips, speed changes and resume reschedule immediately. Paused, missing and final lyrics need no polling; stale provider replies are discarded. |
| App updates | Keep compatible audio/artwork caches across ordinary APK updates. Schema migration, bounded eviction and manual clearing still apply. |

The bitmap cache keeps its 20% heap allowance with a 64 MiB ceiling. Encoded Coil and embedded-art disk caches are capped at 100 MiB each. Derived caches retain up to 128 palette seeds, 64 mesh palettes, 64 small artwork meshes and eight blurred 128-pixel images. Pending request entries are removed when their readers finish.

## Verification and measurement limits

Regression coverage checks migration, format ceilings, distinct byte keys, bounded warming/eviction, lookup reuse and cancellation, lyric positive-result limits, settings/provider identity, stale replies and AutoMix gating. Playlist/artwork coverage also checks account isolation, refresh invalidation, concurrent-reader cancellation, cache policies and offline fallback.

Earlier controlled fixtures showed a three-page playlist with a 300 ms loader per page reusing all three successful pages during freshness, and a three-minute lyric fixture using 34 boundary wakeups instead of 360 half-second polls. An Android 35 upgrade retained 283 image-cache files and reopened a 100-track playlist with the correct duration.

Those fixtures demonstrate avoided work, not measured physical-device battery savings. Earlier software-GPU emulator scroll traces did not establish a frame-rate improvement. Canary 9 adds physical S22 frame-deadline measurements above. A specific startup time, sustained FPS gain or battery reduction has not been established.
