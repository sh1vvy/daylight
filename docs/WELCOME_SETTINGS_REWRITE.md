<!-- DAYLIGHT-ATTRIBUTION
Daylight modifications by sh1vvy.
Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
Daylight changes: 2026-10-13 through 2026-10-13.
Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
Original authors' notices and licenses remain applicable to their material.
-->

# Welcome and settings — Canary 21

Daylight's new Android entry and settings presentation is implemented under
`com.sh1vvy.daylight.welcome` and `com.sh1vvy.daylight.settings`.

## Entry

First-use entry presents YouTube sign-in and a guest option. An existing signed-in
session bypasses it. Guest confirmation and successful authorization persist a
separate completion flag; opening or cancelling sign-in does not. Settings can
preview the welcome without signing out or replacing account data.

The reference's soft reveal is interpreted as a finite procedural wing bloom
using Daylight's existing butterfly and theme colors. It stops after settling,
uses no video/network asset and respects Reduce animations. A scrollable safe-area
layout retains both buttons on small windows and with larger text. The official
YouTube icon is taken from `https://brand.youtube/`, unmodified except for SVG to
Android vector conversion; its trademark remains Google's.

## Settings

A compact settings button beside the profile opens the hub. Search, account,
three theme previews and six category routes replace the previous accordion.
Selected routes and query survive navigation/recreation. Category IDs are stable
and independent of translations. Search checks localized category titles and
summaries plus topic keywords; individual controls retain their existing search
keywords. Back returns from a category to the hub, then to the previous screen.
Account/integrations, Jam, credits, equalizer, backup and import remain available.

Settings uses theme-tonal grouped surfaces, rounded icons, inset touch targets and
short connected page transitions. The hub does not collect all controls or playback
telemetry. Only the selected controls page is composed; high-frequency audio
telemetry is observed only in Playback. Lazy hub items have stable keys and no
continuous decoration clock.

## Selective Material 3 Expressive

Google Sans Flex v5.000's Android variable font is bundled from the official
`googlefonts/googlesans-flex` release. Compose caches fixed weight/optical-size/
roundness variants; there is no live font-axis animation or download. Original
OFL, author and trademark files are packaged under credits and kept under
`docs/licenses`. Existing selectable lyric fonts and script fallbacks remain.

Pull-to-refresh now uses Material 3's native expressive shape LoadingIndicator.
Drag progress drives the determinate shape, then the indeterminate morph runs
only while refreshing. Reduce animations uses a static progress shape. Actual
playback/download progress indicators retain their purpose and appearance.

Material 3 `1.4.0-alpha18` supplies this API while remaining compatible with the
existing Android Gradle plugin. The newest expressive beta requires AGP 9.1 and
Compose 1.12; this canary avoids that wider tooling migration. This is an internal
candidate, so the experimental dependency must continue receiving regression
checks before a public Dev release. No global expressive color scheme or layout
has been applied. Player, Home, Library and Search keep their existing flow.

## Rewrite boundary

The entry UI, persisted entry choice, settings navigation vocabulary, hub layout
and category transitions are new Daylight code. Existing `AppSettings` storage,
control actions, account WebView, auth/provider integration, audio/output settings,
backup and credits are deliberately retained through adapters. This does not
constitute a full rewrite of settings storage, authentication or the audio engine.
The original GPL and upstream notices remain in force; Google font/brand notices
and Material/Compose licenses remain applicable to their own material.

## Validation

Unit tests cover route restoration, multiword/localized search, unchanged settings
and library coordinators. Native emulator tests cover guest persistence, category
restoration, all three themes, larger type, retained control behavior, search/dock
flow and the existing player. Logs and screenshots are kept in
`output/canary-0.2.2-canary.21/qa/`. No real account is used by these fixtures.
The S22 was not connected to adb during this canary's validation.
