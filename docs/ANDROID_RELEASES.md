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

## Internal canaries

Public Dev updates resume with **0.2.2-dev.7**, Android code **27**, following
eight internal canaries through **0.2.2-canary.8**, code **26**. This Dev release
updates the same package and signer and restores public Dev update checks.
Internal canaries disable their update checker and remain manually installed.
Do not publish, tag or push a canary without the owner requesting it. Leave the
stable release and website download badge unchanged. Future public Dev updates
continue at **dev.8** with a code greater than every installed Dev or canary.

## Development updates

Shivvy has requested that ongoing updates be published to **Daylight Dev** until
he asks to promote a stable release. The next stable version is **0.2.2**; the
current public release remains **0.2.1**, code **12**.

Development builds use **0.2.2-dev.1**, **0.2.2-dev.2**, and so on. The first has
Android version code **13**; increment the Dev code with every new Dev release.
These values live in the `dev` flavor in `app/build.gradle.kts`, independently of
the production version. Keep the existing development package and debug signer
so users can install over Daylight Dev without losing library or settings.

For each requested development update, run the Android/shared tests and build
`:app:assembleDevDebug`. Publish a GitHub **prerelease**, explicitly **not latest**,
tagged `v0.2.2-dev.N`, containing only `daylight-dev.apk` and `SHA256SUMS.txt`.
Update the README's direct Dev download link to that tag after verifying its assets.
Do not replace the stable APKs or change the Jam website's public download badge.
Source fixes can be pushed to main without publishing a production APK.

The new Dev updater reads GitHub's release list and chooses the newest compatible
Dev prerelease or stable release. Stable uses `/releases/latest` and rejects
prereleases. Numeric Dev suffixes compare numerically, so dev.10 follows dev.9.
Previously installed Dev 0.2.1 builds still check only stable: install the first
Dev prerelease manually to opt into this channel. Later Dev builds can update
through the in-app popup.

Stable releases remain user-triggered, roughly weekly. A cadence alone does not
authorize publishing or promoting a stable version.

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
   Set the production version explicitly. For the Dev asset attached to this
   stable tag, set `developmentVersionName` to the same clean release version and
   increment its code beyond the last Dev prerelease's code. The channels have
   independent Android version codes; never attach an older-code Dev APK or an
   APK whose version name differs from the release tag. Update the Jam badge.
2. Run the Android/shared tests and build `:app:assembleDevDebug` and
   `:app:assembleProdRelease`. Verify the universal APKs’ package, version code
   and signing certificates with the Android build tools.
3. Commit and push the source, then create the matching `vX.Y.Z` tag/release.
   Upload the universal development APK as **`daylight-dev.apk` first**, followed
   by the signed universal production APK as **`daylight.apk`**. Attach checksums.
4. Verify the release and downloads while it is a draft, then publish it as a
   normal latest release. Drafts never trigger startup checks; prereleases are
   visible only to the new Dev channel.
5. Deploy the Jam page after its download links resolve. If styles or scripts
   changed, bump their immutable asset filenames and keep the prior files available.

The first-asset ordering is required for installations through 0.1.9: their old
updater takes the first uploaded APK. Starting with 0.2.0, `AppRelease` chooses the
matching package by asset name, independent of upload order. Never put an unsigned
or architecture-specific APK ahead of the compatible development APK.

## Updating in the app

An older installed version checks its release channel once at cold startup and
shows an update popup. Download runs inside Daylight; Install opens Android’s
package installer. Android may first ask to allow installs from Daylight; after
granting that setting, tap Install again. The installed version does not offer
the same release again. There is no background polling or automatic installation.
