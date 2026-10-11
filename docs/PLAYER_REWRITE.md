<!-- DAYLIGHT-ATTRIBUTION
Daylight modifications by sh1vvy.
Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
Daylight changes: 2026-10-11 through 2026-10-11.
Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
Original authors' notices and licenses remain applicable to their material.
-->

# Daylight player preview

Canary 18 introduces an optional presentation under **Settings → Appearance →
Player style → Daylight**. Existing installs default to Current. Switching styles
does not replace the queue, recreate the audio session, or change lyric sources.

The new implementation lives in
`sharedUi/src/jvmSharedMain/kotlin/com/sh1vvy/daylight/player/`. It owns its track
and session models, responsive layout, controls, panel gestures, lyric timeline,
playhead interpolation and lyric drawing. It imports no original app models or
player components. This code was implemented directly from the visual reference;
it does not copy the original player or lyric rendering implementation.

`PlayerExperienceScreen` is the transitional boundary. It converts the existing
session and lyric provider responses, applies the lyric offset once, and forwards
user actions to the existing engine. Existing Canvas playback, artwork resolution,
Automix cover dissolve/progress glow, output controls and lossless detection are
supplied through presentation slots. The normal player remains available intact.

## Interaction

- Artwork extends into the background without a framed cover. Tap opens the
  artwork viewer; horizontal swipes change tracks when permitted.
- The heart sits left of the title, the anchored menu right. Native Material
  play/pause glyphs crossfade and scale briefly inside a themed tonal button.
- Swipe up on the artwork player to reveal the queue with an upward spring.
  Swipe down on the queue handle, press Back, or tap Queue to return. Queue
  scrolling does not turn into a track-change gesture. The playback deck stays
  available; the queue button also provides an accessible alternative to swiping.
- Lyrics use a separate bounded lazy list. Timed lines follow the audio clock;
  word/syllable progress is drawn using provider timings. Tapping a timed line
  seeks. Manual scrolling stops following until Resume lyrics is pressed.
  Plain lyrics remain readable without fabricated timing. Known missing lyrics
  disable the lyrics button. Providers and offset remain available.
- Themes supply readable controls and ink. Reduced motion removes panel/press
  animation. System insets and portrait/landscape constraints are respected.

## Performance and remaining scope

The playhead is observed only by the progress and lyric drawing paths. The root
does not subscribe to each playback tick. Lyrics prepare one sorted time index,
reuse row text layouts, and compose visible rows. Only the active line draws
word progression; paused playback stops the presentation frame clock. There is
one existing audio owner and one Canvas decoder, with no extra provider search,
audio cache, background blur animation or duplicate playback session.
The cover holds its last decoded frame while paused, during Automix's cover
handoff and while the lyric/queue panel or artwork viewer is open.

This is a presentation rewrite, not a replacement audio engine. The adapter and
its inherited dependencies remain subject to their existing licenses. A later
engine/provider rewrite is separate work. Translation, romanization, lyric-card
sharing, queue drag reordering and the Jam context header are not yet presented
in the new mode; Current retains those controls. The new mode still obeys Jam
queue and seeking permissions and uses the same playback session.

Native emulator checks cover six theme/glass combinations, retained Current,
heart/menu placement, lyric seeking, queue actions and gestures, Automix artwork,
and actual decoded animated-cover frames. Phone observations and exact results
are stored with the local canary in `output/canary-0.2.2-canary.18/qa/`.
