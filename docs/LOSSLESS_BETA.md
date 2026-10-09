# Lossless beta research and implementation

Checked on 9 October 2026. This is an opt-in Android streaming feature, disabled on fresh installs. The user approved the proposed playback integration in this chat before it was connected.

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

## Behavior

Settings → Playback → Lossless beta starts disabled. It applies to newly started queues on an unmetered network; music videos and video-origin tracks retain YouTube. No request reaches the extra service while the option is disabled. Downloads and Cast receivers retain their existing YouTube path.

The connector requires a playable catalogue entry, matching explicit/clean edition when known, matching duration within two seconds, and either a matching ISRC or an exact normalized title and shared artist. FLAC STREAMINFO independently verifies codec, sample rate, bit depth, channel count and recording duration. Headers, filenames, provider labels and file-size estimates cannot establish lossless or hi-res. Uncertain matches fall back.

Search and header validation share a five-second budget, are cancellable and bounded in size. Up to three independently matching candidates can be checked, so one mislabeled AAC file does not hide another genuine FLAC. A selected rendition stays pinned for the life of that playback URI, including seeks and continuation reads. Each new play receives its own URI marker. The beta bypasses the existing audio disk cache so FLAC cannot be mixed with YouTube bytes. YouTube URL warm-up remains available; beta byte prefetch is suppressed.

Dev.4's original 1.5-second budget was too short: live Taylor Swift searches took 1.2–1.6 seconds before header validation, which needed another 0.7–1.5 seconds. Dev.5 corrects this. Home and All-search rows that omit duration now reuse timing returned by the existing YouTube extraction; no additional metadata endpoint is called. Unknown or mismatched duration still rejects a substitution.

YouTube warm-up bitrate telemetry is excluded from FLAC decoder statistics; it describes the unused fallback URL, not the selected lossless file.

The Wi-Fi quality picker includes Lossless beta and shows the separate YouTube fallback quality. Cellular retains the YouTube choices. The main player's Lossless / Hi-Res Lossless badge is gated on the actual decoder's codec; a provider selection alone cannot earn it. While checking, it says Checking lossless. If the measured stream is YouTube Opus/AAC after a beta miss, the player names that actual codec and Stats for nerds includes the reason (timeout, unmatched recording, non-FLAC file, missing duration, excluded playback or unavailable source).

A failing selected stream uses the existing one-way YouTube recovery path, preserving song and position. Explicit Revert to original bypasses the beta. Joining a Jam rebuilds any beta-marked queue entries as direct YouTube items and preserves the current position; newly built Jam items never get a beta marker. Party synchronization, queue metadata and the server protocol retain their existing behavior.

## Limits and validation

This beta verifies the delivered FLAC format. It does not claim that a file was never transcoded upstream, or that Android outputs it bit perfectly. The existing output precision and negotiated-output display remain in place. Exclusive USB output is a separate change and has not been enabled here. Offline lossless downloads, a provider picker, persistent FLAC caching, automatic source updates and mixed-source Jams are deferred.

Local HTTP tests cover off/default behavior, edition/recording rejection, real FLAC header parsing, AAC rejection, malformed and oversized replies, range validation, cancellation, time budget, no redirect impersonation, YouTube fallback, rendition pinning and Jam/metered/video policy. Existing Android online-source policy tests still ensure that legacy modules and add-ons stay disabled.
