# Lossless streaming in Daylight Dev.6

Updated 9 October 2026. Lossless remains optional; fresh installs use normal audio.

## Quality choices

Settings → Playback → On Wi-Fi offers four tiers:

| Tier | Ceiling |
| --- | --- |
| Low | Lower-bitrate YouTube audio |
| High | Best available YouTube audio |
| Lossless | Verified FLAC up to 24-bit / 48 kHz |
| Hi-Res Lossless | Verified FLAC up to 24-bit / 192 kHz |

Lossless uses unmetered connections. Cellular, metered Wi-Fi and an unknown/offline connection retain normal audio. Previously enabled Dev.4/5 beta preferences migrate to Hi-Res Lossless; disabled preferences stay off. Eligible noncurrent queue entries refresh when the lossless tier or connection changes. The current file stays fixed through seeks and continuation reads.

Music videos, video-origin tracks, local files, explicit original-version pins, Jams, downloads and Cast keep their existing playback paths. Jams, downloads and Cast use YouTube. A missing or uncertain FLAC match also uses YouTube.

## Verification and playback

The Monochrome-compatible connector requires a playable entry, the explicit/clean edition when known, duration within two seconds, and either a matching ISRC or an exact normalized title with a shared artist. A 42-byte FLAC STREAMINFO read verifies codec, rate, depth, channels and duration. Home/search rows without duration reuse timing from the existing YouTube extraction. Unknown duration cannot qualify a match.

Search and header checks have a five-second budget including waits for shared lookups. At most three matching candidates are checked. Requests are cancellable, response sizes are bounded, and redirects are refused. Up to 128 recording-and-quality lookup results are retained: verified results for ten minutes, stable misses for thirty seconds, and no cached network failures. Concurrent requests share a lookup.

Only decoder-confirmed lossless earns the main player's static Lossless or Hi-Res Lossless label. Ordinary audio has no quality badge; fallback reasons remain in Stats for nerds. Hi-Res means a lossless rate above 48 kHz or depth above 24 bits. AutoMix cannot activate while Hi-Res is selected or actually playing, and its saved preference survives the temporary restriction.

A failed selected stream takes the existing one-way YouTube recovery path, preserving song and position. Revert to original bypasses lossless. Joining a Jam switches marked items to YouTube without changing queue identity or the Jam protocol. After leaving, noncurrent entries return to the selected tier; the currently opened YouTube file stays fixed. Explicit original-version pins and error-recovery fallbacks remain intact.

## Caching and warming

FLAC now uses persistent audio caching under a separate recording/header-fingerprint key. Prepared playback and queue warming share that exact key; FLAC never shares YouTube byte ranges. Lossless fallback keys also identify the selected YouTube rendition. A committed file remains pinned across reopening and seeking; an unopened warmed choice is rechecked against the active tier, connection and Jam state.

The normal cache defaults to 512 MiB. Either lossless tier raises its minimum to 1 GiB; settings cannot lower it below that floor while enabled. The existing bounded LRU still enforces the chosen budget. Larger existing limits and explicitly selected Unlimited are preserved.

The next five songs are prepared sequentially after the current buffer is ready. Each gets its source search and a short opening: 256 KiB for normal audio, 2 MiB for Lossless or 4 MiB for the Hi-Res selection. Wi-Fi may then cache more of the immediate next song, capped at 64 MiB or one sixteenth of the cache budget. The current song, previous two played songs and upcoming openings receive eviction priority, within the same total limit. Pause, queue, quality and connection changes stop stale workers.

See [performance notes](PERFORMANCE.md) for the accompanying lyric cache and earlier playlist/artwork improvements.

## Limits and validation

File verification establishes the delivered FLAC format, not its mastering history or bit-perfect Android output. Existing output precision and negotiated-output reporting remain unchanged. Exclusive USB output, lossless downloads, a provider picker and mixed-source Jams are separate work.

Regression coverage includes preference/cache migration, both quality ceilings, header and range validation, edition/recording matching, timeout/cancellation, shared lookup reuse, warmed-choice eligibility, rendition pinning, separate cache keys and bounded warming. Native Android checks verify actual decoder format and seeking; availability still depends on the source.

## Sources checked

| Project | What it actually does | Daylight decision |
| --- | --- | --- |
| [LastWave Native](https://github.com/Clash-Projects/LastWave-Native/blob/55b6bb890c6a9904eeaba347296aad209bfd4df3/app/src/main/java/com/lastwave/app/data/lossless/LosslessMusicApi.kt) | A user-supplied add-on matches YouTube catalogue tracks to external audio. Its private add-on authentication belongs to that client. | Keep YouTube catalogue identity. Do not reuse its private credentials or assume its server accepts Daylight. |
| [Spotube lossless plug-in](https://github.com/kipavy/spotube-plugin-lossless-sources/tree/f191ba456033bf1dd5a5bc4c61991cec957f601d) | Searches Monochrome and public HiFi API servers, falling back to YouTube. Its source list is refreshed by an external probe. | Use a small HTTP adapter, without loading remote executable plug-ins or importing its mutable host list. Verify the file rather than trusting a preset or inferred bitrate. |
| [Monochrome client](https://github.com/monochrome-music/monochrome/blob/eb602ad4f8f74e0307f29906fc78a32f08a34f87/js/tracks-api.js) | Public track search and direct track URLs; the client declares every track FLAC. | Suitable for a limited beta after actual audio-header checks. Some returned files are AAC despite the client declaration. |
| [HiFi API](https://github.com/binimum/hifi-api/tree/bf46988bff97bd2609a4fe92c91b573b60c41b8f) | Uses Tidal accounts for streams; author reports account blocking and playback queues. Search can work while playback fails. | Do not ship a pool of unverified public instances. An independently configured backend could be considered later. |

YouTube itself [publishes lossy AAC/Opus streams](https://support.google.com/youtubemusic/answer/9076559?hl=en). Re-encoding those as FLAC would not recover the original audio.

## Live observations

Requests identified themselves as Daylight; no borrowed token, Origin or browser identity was used. Only small byte ranges were read, and no songs were downloaded into the repository.

| Monochrome recording | Result measured from the file |
| --- | --- |
| Daft Punk — Get Lucky, 155142501534011392 | MP4/AAC (`mp4a`), rejected by the lossless connector |
| Billie Eilish — BIRDS OF A FEATHER, 154012568153755648 | FLAC, 24-bit / 44.1 kHz |
| Radiohead — Weird Fishes / Arpeggi, 154043681593102336 | FLAC, 16-bit / 44.1 kHz |
| Taylor Swift — Lover, 154014344886095872 | FLAC, 24-bit / 44.1 kHz |

The sampled HiFi API instance answered catalogue requests but returned HTTP 403 for the sampled stream. These observations establish current reachability and encoding, not full catalogue coverage, provenance of the master, or guaranteed future uptime. Monochrome documents access restrictions for self-hosted web clients; Daylight does not spoof its identity to evade them. A refusal or changed protocol becomes a normal YouTube fallback.
