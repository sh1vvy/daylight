<div align="center">

<img src="docs/assets/daylight-banner.png" alt="Daylight — Android music player" width="100%" />

<p><strong>An open-source Android music player.</strong><br />YouTube Music streaming, optional lossless audio, synced lyrics and shared listening.</p>

<p>
<a href="https://github.com/sh1vvy/daylight/releases/latest"><img src="docs/assets/release-badge.svg" alt="Latest stable release" /></a>
<img src="docs/assets/android-badge.svg" alt="Android 8.0 or newer" />
<a href="LICENSE"><img src="docs/assets/license-badge.svg" alt="GPL-3.0 license" /></a>
</p>

<p>
<a href="https://github.com/sh1vvy/daylight/releases/latest/download/daylight.apk"><img src="docs/assets/download-android.svg" alt="Download Daylight Stable for Android" width="260" /></a>
<a href="https://github.com/sh1vvy/daylight/releases/download/v0.2.2-dev.9/daylight-dev.apk"><img src="docs/assets/download-dev.svg" alt="Download Daylight Dev for Android" width="260" /></a>
</p>

**Stable 0.2.1** · **Dev 0.2.2-dev.9**

[Website](https://daylight.sh1vvy.com) · [Release notes](https://github.com/sh1vvy/daylight/releases) · [Daylight Jam](https://jam.sh1vvy.com) · [Report an issue](https://github.com/sh1vvy/daylight/issues)

</div>

## Screenshots

<p align="center">
<a href="docs/screenshots/home-dev8.webp"><img src="docs/screenshots/home-dev8.webp" alt="Daylight Home with Quick picks, Play my mix and the Discover navigation tab" width="31%" /></a>
<a href="docs/screenshots/player-dev8.webp"><img src="docs/screenshots/player-dev8.webp" alt="Now playing with full-screen artwork, synced lyric preview and verified Lossless playback" width="31%" /></a>
<a href="docs/screenshots/library-dev8.webp"><img src="docs/screenshots/library-dev8.webp" alt="Library with Liked songs, Replay, downloads, Spotify and a two-row playlist layout" width="31%" /></a>
</p>
<p align="center"><sub>Home · Now playing · Library<br />Captured in Dev.8 on Android. Tap any screenshot to view it at full size.</sub></p>

## Features

| Feature | Details |
| :--- | :--- |
| **YouTube Music** | Search songs, videos, albums, artists and playlists. Sign in for your library and personalized recommendations. |
| **Lossless audio <sup>BETA</sup>** | Optional verified FLAC playback, with Lossless and Hi-Res Lossless quality tiers. The player labels the format actually playing. |
| **Listen together** | Create or join a Jam, share an invite link and listen in sync with a shared queue. |
| **Automix <sup>BETA</sup>** | Beat-aware transitions with a subtle progress glow and a blend between the current and next cover. |
| **Quick picks and Play my mix** | Start with familiar tracks and recommendations, or build an ongoing personal mix with one tap. Tune recommendation languages in settings. |
| **Synced lyrics** | Follow timed lyrics, with optional translation and romanization. |
| **Library and playlists** | Open your cached Liked songs list immediately. Edit playlist names and covers, and find recently used playlists first. |
| **Spotify playlists** | Play connected Spotify playlists through matched YouTube Music tracks. Played playlists appear in Library and Listen again. |
| **Themes and artwork** | Dark, Light and Pink Clouding themes, optional liquid glass, full-screen artwork and animated covers. |
| **Playback tools** | Offline downloads, local audio, equalizer, crossfade, sleep timer and queue controls. Disliked tracks stay out of automatic playback. |
| **Replay and integrations** | Listening statistics, Android home-screen widgets, Last.fm scrobbling and Discord presence. |

These features describe the current development build. Lossless availability depends on the track and connection; Jams use YouTube Music audio. Automix supports normal and Lossless audio, with Hi-Res excluded.

## Feature previews

<table>
<tr>
<th width="50%">Automix <sup>BETA</sup></th>
<th width="50%">Synced lyrics</th>
</tr>
<tr>
<td align="center"><a href="https://daylight.sh1vvy.com/#automix"><img src="docs/previews/automix.gif" alt="Recorded Daylight Automix transition between two tracks" width="300" /></a></td>
<td align="center"><a href="https://daylight.sh1vvy.com/#lyrics"><img src="docs/previews/synced-lyrics.gif" alt="Recorded lyrics scrolling and highlighting in sync with playback" width="300" /></a></td>
</tr>
</table>

<p align="center"><sub>Recorded in the app. Select a preview to open its section on the website.</sub></p>

### Liquid glass

<p align="center">
<a href="https://daylight.sh1vvy.com/#glass"><img src="docs/previews/liquid-glass.gif" alt="Daylight's glass navigation refracting the artwork as the home feed scrolls" width="720" /></a>
</p>

Glass controls are optional on Android 12 and newer. Navigation and the compact player also work with glass disabled.

## Themes

<p align="center">
<a href="docs/screenshots/home-dev8.webp"><img src="docs/screenshots/home-dev8.webp" alt="Dark theme" width="31%" /></a>
<a href="docs/screenshots/theme-light-dev8.webp"><img src="docs/screenshots/theme-light-dev8.webp" alt="Light theme" width="31%" /></a>
<a href="docs/screenshots/theme-pink-dev8.webp"><img src="docs/screenshots/theme-pink-dev8.webp" alt="Pink Clouding theme" width="31%" /></a>
</p>
<p align="center"><sub>Dark · Light · Pink Clouding <sup>BETA</sup><br />Follow the system theme or choose one in Appearance settings.</sub></p>

<details>
<summary><strong>Discover and the player menu</strong></summary>

<p align="center">
<a href="docs/screenshots/discover-dev8.webp"><img src="docs/screenshots/discover-dev8.webp" alt="Discover with new releases, moods and genres" width="42%" /></a>
<a href="docs/screenshots/player-menu-dev8.webp"><img src="docs/screenshots/player-menu-dev8.webp" alt="Compact player menu with playlist, queue, sharing and dislike actions" width="42%" /></a>
</p>

</details>

## Download and install

Requires **Android 8.0 or newer**. Download the APK for your channel, open it and approve installation when Android asks.

| Channel | Version | Download |
| :--- | :--- | :--- |
| **Stable** | 0.2.1 | [daylight.apk](https://github.com/sh1vvy/daylight/releases/latest/download/daylight.apk) |
| **Dev** | 0.2.2-dev.9 | [daylight-dev.apk](https://github.com/sh1vvy/daylight/releases/download/v0.2.2-dev.9/daylight-dev.apk) |

Stable and Dev install separately. Keep the same channel when updating to preserve its data. Public builds offer an in-app update prompt; Android still asks you to confirm installation. From an internal Canary, install the matching Dev APK manually once to resume public Dev updates.

Sign in to YouTube Music for personalized recommendations, your online library, **Play my mix** and **Jams**. General search and playback also work without sign-in.

## Build and contribute

Daylight currently supports Android. Start with the [development guide](docs/DEVELOPMENT.md) for requirements, builds and tests. Report reproducible problems in [Issues](https://github.com/sh1vvy/daylight/issues), including your app version, Android version and device model.

[Release guide](docs/ANDROID_RELEASES.md) · [Performance notes](docs/PERFORMANCE.md) · [Lossless notes](docs/LOSSLESS_BETA.md) · [Jam service](cloudflare-jam/README.md)

## Credits and license

Developed by **[sh1vvy](https://sh1vvy.com)**. Licensed under **[GNU GPL v3.0](LICENSE)**. Original copyright notices and corresponding source are retained.

Based on **BitChord**.

- **Butterfly artwork:** © art by 11 ([_artbyeleven on IG](https://www.instagram.com/_artbyeleven/)).
- **Wordmark:** [vector artwork](docs/assets/daylight-wordmark.svg) supplied by sh1vvy.
- **Typography:** [Inter](https://rsms.me/inter/) by Rasmus Andersson, under the [SIL Open Font License](docs/licenses/Inter-OFL.txt).
- **Discover icon:** [M Yudi Maulana / Noun Project](app/src/main/assets/credits/Discover.txt), under [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/).

<sub>Daylight is not affiliated with YouTube, Google, Spotify, Last.fm or other integrated services. Music artwork in screenshots belongs to its respective owners.</sub>
