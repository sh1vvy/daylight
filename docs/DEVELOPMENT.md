# Develop Daylight

Daylight's supported platform is Android. This guide covers local builds, tests, and optional integrations. For installing the app, use the [latest signed release](https://github.com/sh1vvy/daylight/releases/latest).

## Build for Android

Requirements:

- JDK 17
- Android SDK platforms 36 and 37.0
- Android build-tools 35.0.0
- Android NDK 27.0.12077973
- CMake 3.22.1

Open the repository in Android Studio, or copy `local.properties.example` to `local.properties` and set `sdk.dir` to your SDK directory. Gradle requires a configured Android SDK to include the app module.

```sh
./gradlew :app:assembleDevDebug
./gradlew :app:testDevDebugUnitTest :shared:jvmTest :sharedUi:jvmTest
```

Development APKs are in `app/build/outputs/apk/dev/debug/`. Production APKs use `:app:assembleProdRelease`; without your signing configuration they are unsigned. Configure your own signing key using `keystore.properties.example` before distributing release APKs. See the [release guide](ANDROID_RELEASES.md) for package matching and signing requirements.

| Channel | Android package |
| --- | --- |
| Public | `com.sh1vvy.daylight` |
| Development | `com.sh1vvy.daylight.dev` |

The channels install separately and keep separate application data. Internal Kotlin namespaces and native entry points remain unchanged for engine compatibility; see [acknowledgments](../UPSTREAM.md).

## Test providers

The normal test suite uses local fixtures. The Genius live-provider smoke test is opt-in because its availability depends on the network and external website:

```sh
DAYLIGHT_LIVE_PROVIDER_TESTS=true ./gradlew :app:testDevDebugUnitTest --tests com.music.bitchord.GeniusTest
```

## GitHub builds

The [Android workflow](../.github/workflows/android.yml) runs Android and shared-library unit tests, then produces a development APK and production APKs. Release signing is optional: configure `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` repository secrets to sign production builds.

Without signing secrets, the workflow produces unsigned production APKs and an installable development APK. CI signing keys may differ from the locally published development key, so workflow artifacts are for testing, not automatic replacements for signed release assets. The workflow does not publish releases automatically.

## Optional integrations

Set these in the ignored `local.properties` file or as build environment variables:

| Setting | Purpose |
| --- | --- |
| `LISTEN_TOGETHER_SERVER` | Optional party server override; defaults to `https://jam.sh1vvy.com`. |
| `LASTFM_API_KEY`, `LASTFM_SECRET` | Your Last.fm app credentials. |
| `DISCORD_APPLICATION_ID` | Your Discord application ID for presence artwork and buttons; empty by default. |

Jam uses Daylight's Cloudflare service at [jam.sh1vvy.com](https://jam.sh1vvy.com). Shared HTTPS invitation pages open the app. See the [Cloudflare Jam guide](../cloudflare-jam/README.md) for configuration and free-tier limits. The Go backend in `backend/` is retained as an alternative deployment; its [guide](../backend/README.md) covers that setup.

## Android library and settings

Settings opens with six collapsed categories. Search reveals matching controls
without changing the listener's saved expansion choices. Account & integrations
and Jam remain directly accessible; Credits is at the bottom of Account & integrations.
Replay opens from the Library, including when there is no listening history yet.

Successful playlist creations and song additions are recorded locally and displayed
most recently updated first, including after a server refresh or restart. Viewing,
renaming, cancelled duplicate additions and failed writes do not change this order.
The Library preview and Show all use the same order, and Show all opens at the top.
New creations remain visible while the server's library feed catches up.

Settings uses soft tonal cards and spaced subsections instead of dark dividing
lines. Category icons and descriptions distinguish the groups from their controls.
The Playback Sources menu is removed: YouTube Music is the online playback and
download source, independently of older saved provider preferences. Local files
and network folders remain available; WebDAV and SMB setup is in Downloads & storage.

Playlist credits open a small creator profile using the playlist header's owner,
with available avatar, description and public playlists. Local playlists use a
device profile; unknown owners remain plain text. Album artist links keep opening
artist pages.

Android retains Spotify playlists but no longer fetches Spotify Canvas or submits
listens to ListenBrainz. Last.fm and other motion artwork remain available. Lyrics
translation always follows the app language; retired settings are removed on
startup and backup restore.

Home refresh commits the recommendation feed and Recents together, retaining the
visible Recents shelf if history fails. Old initial-load and pagination responses
cannot overwrite a newer refresh. A pending history request reserves its leading
space without displaying a second placeholder once Recents is already present.

Swiping the task out of Android Recents stops playback by default, including both
crossfade players and the notification. Pressing Home or locking the device still
allows background listening. An explicitly disabled Stop music on close preference
is preserved. The profile photo on Home has no thumbnail ring or surrounding glass
surface. Manage accounts opens Account & integrations, where sign-out remains.

Bottom-tab navigation dismisses Spotify and Discord as well as the other pushed
pages. Spotify playlist details stay above their account/settings entry point;
Back returns to Spotify and tab taps immediately return to their destination.

## More context

- [Performance changes and validation](PERFORMANCE.md)
- [Android release process](ANDROID_RELEASES.md)
- [Acknowledgments and retained source history](../UPSTREAM.md)
- [License](../LICENSE)
