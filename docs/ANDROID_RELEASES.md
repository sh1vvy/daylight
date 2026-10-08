# Daylight Android releases

The latest public release is **v0.2.1**, Android version code **12**. Releases
are published at <https://github.com/sh1vvy/daylight/releases>. The Jam website
downloads the public APK through the stable URL
`https://github.com/sh1vvy/daylight/releases/latest/download/daylight.apk`.

| Release asset | Android package | Signing key | Purpose |
| --- | --- | --- | --- |
| `daylight.apk` | `com.sh1vvy.daylight` | Local production key | Public Android app |
| `daylight-dev.apk` | `com.sh1vvy.daylight.dev` | Existing local Android debug key | Updates previously installed Daylight Dev builds |

Both assets are universal APKs, supporting arm64, ARMv7 and x86_64. The public
APK uses the release build’s R8 optimizations. The development APK retains its
existing package and signer so an in-place update preserves library and settings.

## Interim APK updates

For the next few updates, provide signed APKs for manual installation. Do not
create a GitHub release or tag, upload APKs to a release, or change the live Jam page’s public
download/version badge for these builds. Source changes can still be pushed.
Keep the existing package and signing key so installation updates the app without
losing data. Per Shivvy’s preference, manual refreshes of the current build keep
**version name 0.2.1 and version code 12**. Android supports manually replacing a
signed APK at the same version code. Change the version only when a new version
or public release is requested and the candidate does not already exceed the
latest public version and version code.

The current build and latest public release are **0.2.1**, version code **12**.
Public releases are intended roughly every seven days, when Shivvy explicitly
asks to publish. This cadence does not authorize automatic
publishing. Run the checks below for manual APKs too, then deliver both package
variants with checksums. Install the variant matching the app already installed.

## Signing

The production key is `daylight-release.jks`, referenced by the private
`keystore.properties`. Both are ignored by Git and are never release assets.
Keep a secure backup of both files and the existing development key at
`~/.android/debug.keystore`. Losing a signing key prevents future APKs from
updating that package without uninstalling it. Do not generate replacement keys
for an existing release channel.

The public certificate fingerprints are registered in
`cloudflare-jam/src/asset-links.js` so shared HTTPS Jam invites can open in both
packages. These certificates are public; the private keys remain local.

The Android CI workflow continues to produce unsigned production APKs unless
its optional signing secrets are configured. Public releases must use verified,
signed output from the holder of the production key.

## Publishing

Follow these steps only when a public release is explicitly requested.

1. Check that the candidate's `versionName` and `versionCode` both exceed the
   latest public release. Increase them in `app/build.gradle.kts` only if needed;
   a verified interim APK with a newer version can be promoted without rebuilding.
   Leave `betaSuffix` empty for a normal release. Update the version on the Jam badge.
2. Run the Android/shared tests and build `:app:assembleDevDebug` and
   `:app:assembleProdRelease`. Verify the universal APKs’ package, version code
   and signing certificates with the Android build tools.
3. Commit and push the source, then create the matching `vX.Y.Z` tag/release.
   Upload the universal development APK as **`daylight-dev.apk` first**, followed
   by the signed universal production APK as **`daylight.apk`**. Attach checksums.
4. Verify the release and downloads while it is a draft, then publish it as a
   normal latest release. Drafts and prereleases do not trigger the startup check.
5. Deploy the Jam page after its download links resolve. If styles or scripts
   changed, bump their immutable asset filenames and keep the prior files available.

The first-asset ordering is required for installations through 0.1.9: their old
updater takes the first uploaded APK. Starting with 0.2.0, `AppRelease` chooses the
matching package by asset name, independent of upload order. Never put an unsigned
or architecture-specific APK ahead of the compatible development APK.

## Updating in the app

An older installed version checks the latest release once at cold startup and
shows an update popup. Download runs inside Daylight; Install opens Android’s
package installer. Android may first ask to allow installs from Daylight; after
granting that setting, tap Install again. The installed version does not offer
the same release again. There is no background polling or automatic installation.
