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

- **Colour:** Light and Dark use the existing warm Daylight accent. Pink Clouding
  uses rose. Standard Material controls use explicitly defined tonal containers,
  rather than falling back to Material's default purple palette.
- **Floating controls:** The mini player, navigation group and Search share one
  gently tinted surface. Standard mode uses a lightweight translucent fill,
  theme ink and an accent selection indicator. It has no glass rim or backdrop
  blur. Reduce dynamic blur keeps a solid equivalent for readability. Liquid
  Glass keeps its existing rendering and motion.
- **Typography:** Inter for app text and Inter Display for prominent headings
  and lyrics. The Daylight wordmark remains a separate brand asset.
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

The current canary establishes the palette and floating-control foundation.
Future screen work should follow these rules rather than adding its own material,
spacing or animation style.
