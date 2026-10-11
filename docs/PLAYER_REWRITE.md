<!-- DAYLIGHT-ATTRIBUTION
Daylight modifications by sh1vvy.
Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
Daylight changes: 2026-10-11 through 2026-10-12.
Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
Original authors' notices and licenses remain applicable to their material.
-->

# Daylight player preview

Canary 18 introduces an optional presentation under **Settings → Appearance →
Player style → Daylight**. Existing installs default to Current. Switching styles
does not replace the queue, recreate the audio session, or change lyric sources.

The new implementation lives in
`sharedUi/src/jvmSharedMain/kotlin/com/sh1vvy/daylight/player/`. It owns its track
and session models, responsive layout, controls, panel gestures, surface motion
and progress interpolation. It imports no original app models or player
components. Its presentation slots allow the adapter to supply existing artwork,
output and lyric rendering without embedding those implementations in the scene.

`PlayerExperienceScreen` is the transitional boundary. It converts the existing
session and forwards user actions to the existing engine. Existing Canvas playback, artwork resolution,
Automix cover dissolve/progress glow, output controls and lossless detection are
supplied through presentation slots. The normal player remains available intact.

At the owner's request, Canary 19 replaces the experimental lyric renderer with
the established `LyricsPanel`, including Daylight's integration of
[Accompanist Lyrics UI](https://github.com/6xingyv/accompanist-lyrics-ui). Its
spring-chain attribution, Apache-2.0 license and Settings → Credits entry remain
in place. This part of the new mode is reused licensed code, not an independent
lyric-system rewrite. Provider timing is passed unmodified; its existing playhead
and tap-to-seek helpers apply the chosen timing offset consistently.

Canary 19 adds an independent scene layout and fixed-playhead timeline. The
window adapter supplies the measured mini-player surface/artwork coordinates
and finger/release callbacks; the new layout owns their interpolation, clipping,
cover collapse and compact transport handoff. It reuses the existing host spring
and Android sheet window, which are integration dependencies rather than a
replacement window or audio engine.

## Interaction

- Artwork extends into the background without a framed cover. In the main
  view, tapping opens the artwork viewer and horizontal swipes change tracks
  when permitted. Tapping the small cover in Lyrics or Queue instead returns
  to the full player through the same expanding-cover spring as the panel gesture.
- The heart sits left of the title, the anchored menu right. Native Material
  play/pause glyphs crossfade and scale briefly inside a themed tonal button.
- The full surface expands from and settles into the measured mini-player pill.
  A downward pull tracks the finger before the host spring settles it using
  release velocity. The cover stays full-bleed through the larger descent,
  then folds into the mini cover as compact transport fades in.
  The cover uses the same fold progress for its corner radius, reaching the
  actual scaled mini cover radius before the final surface handoff.
- Swipe up on the artwork player to reveal the queue with an upward spring.
  Swipe down on the shared handle above the song title, press Back, or tap the
  selected panel button to return. This gesture area is separate from the top
  lane, which still minimizes the entire player. Queue
  scrolling does not turn into a track-change or sheet-dismiss gesture. Both
  panels shrink the cover and lift the track into a compact header, without
  opaque floating cards. The playback deck stays
  available; the queue button also provides an accessible alternative to swiping.
  Queue-only playback-mode pills use icons and a contrasting selected fill,
  with accessible state descriptions. They do not appear on the lyrics page.
  No visible Queue heading appears. Inset song labels and bottom content padding
  keep the final rows reachable. A 40dp bottom strip softly blurs and fades the
  queue into the deck, sampling at a third resolution; reduced blur and older
  platforms retain the fade without the blur effect.
- The new timeline moves under a fixed center marker, with a thick upcoming
  segment and thin translucent elapsed segment. Horizontal dragging moves
  audio time opposite to the dragged track; accessibility supports direct
  progress adjustment. Automix glow remains driven by the engine's mix state.
  Visible segment ends stay rounded as the track moves past the viewport. The
  main heart and menu align with the fixed elapsed and remaining timer edges,
  with their full touch targets preserved.
- Lyrics use the established bounded lazy list, spring trail, focus blur and
  word/syllable highlighting. Tapping a timed line seeks; manual scrolling
  pauses following before the existing timers resume it during playback.
  Plain lyrics remain untimed. Known missing lyrics disable the lyrics button.
  No extra Lyrics heading, provider menu or settings button appears in the
  panel. Provider and motion preferences remain in app settings; the main song
  menu retains its existing timing adjustment.
- Themes supply readable controls and ink. Reduced motion removes panel/press
  animation. System insets and portrait/landscape constraints are respected.

## Performance and remaining scope

The playhead is observed only by the progress and lyric drawing paths. The root
does not subscribe to each playback tick. The established lyric renderer reuses
its clock, text layout caches and bounded spring trail, and composes visible rows. Panel and
surface motion read state in measurement, placement or drawing instead of
recomposing the player each frame. Existing lyric blur/glow and reduced-motion
preferences continue to apply. Word progression uses draw-local timing;
paused playback stops the presentation frame clock. There is
one existing audio owner and one Canvas decoder, with no extra provider search,
audio cache, full-screen animated blur or duplicate playback session.
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
actual decoded animated-cover frames and the credited lyric spring motion.
Phone observations and exact results
are stored with the local canary in `output/canary-0.2.2-canary.19/qa/`.
