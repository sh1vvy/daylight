<!-- DAYLIGHT-ATTRIBUTION
Daylight modifications by sh1vvy.
Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
Daylight changes: 2026-10-12 through 2026-10-12.
Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
Original authors' notices and licenses remain applicable to their material.
-->

# Daylight Search

Canary 20 replaces the Android search presentation and request coordinator.
The Search button folds the current navigation/transport dock into a bottom
search field. Its existing player, navigation and Search surfaces morph into
the source selector, editor and Close control; they do not slide in separately.
A source selector for **YouTube Music** and **Library** sits
directly above it. Both rows follow Android's keyboard inset animation.
Suggestions, recent searches and results occupy the area above the dock.

Submitting a query or dismissing the keyboard leaves the field at the bottom.
Close restores the previous tab and its existing scroll state. Playing a result
restores the normal mini-player/navigation while retaining the query and results;
tapping Search opens the editor again. Track results start the existing radio
queue, local results use the local collection, and browse results open their
existing detail pages. Menus, long presses and swipe-to-queue remain available.

## Ownership and integration

- `libraryCore/.../com/sh1vvy/daylight/search/SearchRepository.kt` owns request
  cancellation, query stages, scoped caches, retries and continuation paging.
  It imports no original app models or provider implementations.
- `sharedUi/.../com/sh1vvy/daylight/search/DaylightSearchResults.kt` owns the new
  results/history presentation and row actions using provider-neutral hits.
- `app/.../com/sh1vvy/daylight/search/SearchDock.kt` owns the bottom editor,
  source selector and keyboard-following dock motion. `SearchDockSurfaceMorph`
  captures the normal dock's actual resting bounds; the search scene interpolates
  those shared surfaces and hands them back after closing. Result scrolling
  leaves the captured navigation geometry intact. Labels and editor content
  follow the same measured rectangles, including when the mini-player starts
  collapsed; they do not appear ahead of their surfaces.
- `SearchExperience` maps the existing provider/local-library models to the
  new presentation. `MainViewModel` supplies provider calls and compatibility
  flows, while `MainActivity` supplies navigation, playback and account actions.

The YouTube Music implementation, local MediaStore reader, history persistence,
audio engine, account management, glass renderer and normal navigation component
remain existing integration dependencies. This change does not constitute a
complete app or provider rewrite. Their licenses and attribution remain intact.

## Request and rendering costs

Text suggestions wait 160 ms and media previews wait 300 ms after typing stops.
Each new edit cancels both earlier request lanes. The typed term remains usable
when preview requests fail. Generation and provider identity checks reject late
responses, including calls that do not cooperate with cancellation. An identity
change also retires spinners and private cached rows.

Result/preview caches expire after two minutes, with at most 12 entries and
600 rows per cache. Text completions keep at most 24 entries. Keys include
provider/account scope, language, normalized query and filter without storing
credentials. Oversized active lists retain the last bounded cached prefix and
its matching continuation, so reopening can resume rather than skip rows.

Pagination retains visible results, deduplicates stable IDs, stops repeated
continuations and requires an explicit retry after failure. Appending a page
does not repeatedly warm the unchanged first track. The UI composes visible
rows with stable keys/content types and requests small artwork. Loading uses
static placeholders rather than another animation clock. Dock progress and IME
insets are read in measurement/placement; reduced motion removes its spring.
The temporary shared glass surfaces retire completely at rest. Result filters
use compact text tabs with an accent underline; flat rows leave the page's theme
visible instead of adding filled containers behind each match.

The Search window temporarily uses resize inset handling to avoid Android
panning the page as the dock already moves with the keyboard. The host's prior
window mode is restored on leaving Search. Implementation guidance came from
Android's [keyboard animation guidance](https://developer.android.com/develop/ui/compose/system/keyboard-animations),
[Compose insets](https://developer.android.com/develop/ui/compose/system/insets-ui)
and [performance practices](https://developer.android.com/develop/ui/compose/performance/bestpractices).

## Validation

Core tests cover debounce, stale results, account/filter identity, local-source
cancellation, cache expiry/eviction, oversized resumable caches, page deduplication
and retries. Native app tests cover keyboard docking, retained queries, source
switching and returning to the previous tab across Dark, Light and Pink with
glass on/off. Deterministic result fixtures check track/browse/menu actions,
swipe-to-queue and page retry without depending on provider timing.
A playing-dock fixture captures opening/closing frames and verifies transport
controls remain intact after the round trip.

Exact build, native and Galaxy S22 observations are recorded with the manual
Canary APK under `output/canary-0.2.2-canary.20/qa/`. Public Dev remains unchanged.
