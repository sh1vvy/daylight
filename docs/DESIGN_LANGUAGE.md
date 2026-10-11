<!-- DAYLIGHT-ATTRIBUTION
Daylight modifications by sh1vvy.
Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
Daylight changes: 2026-10-11 through 2026-10-11.
Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
Original authors' notices and licenses remain applicable to their material.
-->

# Daylight design language

Daylight uses soft Material surfaces with an optional Liquid Glass finish.
Components share the same visual rules and retain their behavior in both modes.

- **Colour:** Dark uses charcoal and plum with cream ink, blush actions, lavender
  containers and a restrained amber glow. Light uses ivory, navy ink and blue
  actions with a soft azure/lime field. Pink Clouding uses a petal-to-chartreuse
  wash, deep rose ink and rose containers. Raw neon swatches are reserved for
  tints or containers with dark ink; primary text colours are adjusted for
  readability. Cards, fields, dialogs and menus use explicit Material roles.
- **Floating controls:** The mini player, navigation group and Search share one
  gently tinted surface. Standard mode uses a lightweight translucent fill,
  theme ink and an accent selection indicator. It has no glass rim or backdrop
  blur. Reduce dynamic blur keeps a solid equivalent for readability. Liquid
  Glass keeps its existing rendering and motion.
- **Typography:** Inter for app text and Inter Display for prominent headings
  and lyrics. Headings use subtle negative letter spacing (-0.25sp to -0.7sp,
  scaled to text size); smaller body text retains its readable default spacing.
  The Daylight wordmark remains a separate brand asset.
- **Geometry:** Rounded rectangles for content and menus; capsules for compact
  actions and floating navigation; circles for avatars and standalone icons.
  Material components use one 8/12/16/24/28dp corner scale, supplied by the app
  theme rather than redefined per screen.
  Related controls share edge alignment and padding. Keep the existing layout
  and touch targets when changing a surface treatment.
- **Spacing:** Use the existing 16dp page gutters, 8/12/16dp content gaps and
  24dp section separation as the baseline. Align related titles, art and actions;
  retain larger insets needed by system bars, gestures and accessible controls.
- **Hierarchy:** Clear titles, restrained supporting text, quieter secondary
  controls and one accent for the active action. Avoid decorative dividers when
  spacing already separates content.
- **Motion:** Preserve direct manipulation, column snapping, navigation folding
  and connected artwork transitions. Do not introduce an animation that blocks
  input or makes content change position while it is being read. Respect reduced
  motion and blur preferences.
- **Performance:** Reuse theme colours and shared components. Standard floating
  controls do not need a live blur pass. Keep network updates out of scrolling
  frames and use cached, correctly sized artwork.

Full-page theme atmospheres stay fixed behind scrolling content. The three
colour stops and one radial glow use cached native gradient brushes, without
animated noise, bitmap backgrounds, dynamic blur or scroll-driven work. The
Glass renderer records the same atmosphere; standard floating surfaces remain
lightweight and carry matching ink/selection colours. Top and bottom fades
follow their respective gradient edges. Existing artwork-driven detail pages
and animated sleeves retain their own treatment. Secondary settings overlays
paint an opaque gradient so preserved screens cannot bleed through. Ordinary
song rows and local-library tabs stay transparent; swipe actions paint only
the exposed area. Menus and audio-inspection popups use tonal containers and
matching ink rather than fixed black/white plates. Text and
active labels meet 4.5:1 across sampled backgrounds and all Material containers.

Home follows the same palette with direct artwork, 22sp section headings and
30dp shelf separation. Quick Picks uses a centered cover carousel with 16dp
corners, a larger focused cover with a soft shadow and smaller 24-degree tilted
neighbours contained inside the shared 16dp page gutters. One continuous drawing
path keeps sleeves clear of each other at the depth handoff; captions fade at
the midpoint. Native Android decay lets a faster flick coast through up to three
covers before one soft spring settles the final cover; gentle swipes move one.
Home's snapping track columns use the same decay and settling spring. Extreme
release speeds are smoothly restrained relative to item size. Paging loops the 12-track pool, keeping neighbours
on both sides at the wrap; captions stay centered below. Only nearby pages
compose, and accessibility exposes the real track count. Tap selects a
related-song station; hold opens song actions. Play my mix keeps its existing
pill below. Other track shelves retain three-song groups with inset separators
and 48dp overflow targets. Collection artwork uses 8dp corners (artist portraits
stay circular), with captions below. Press feedback uses a small, interruptible
spring on the drawing layer; reduced motion removes scaling and rotation.

Home displays a bounded prepared snapshot. Refresh replacements wait until the
page returns to rest at the top, including newly fetched tail sections. Lazy
layouts retain half a viewport ahead and behind; a 20-image/two-request warmup
reuses the existing bounded artwork cache. There is no per-shelf entry animation
or network trigger attached to scrolling.

Home's final row clears the measured floating controls by 16dp. Folding those
controls releases the unused space at the bottom; padding reads happen during
layout instead of recomposing shelves on each animation frame. System navigation
and accessible control sizes are included. No decorative footer fills the gap.

Library artist rows keep circular portraits at a fixed size while artwork loads.
Opening a Library artist shows the user's liked songs from their credits; global
artist links elsewhere retain the catalogue destination.
