# Daylight Dev.8 validation

**0.2.2-dev.8 · Android code 31 · development prerelease**

This requested Dev release includes Canary 9–11 and the compact player menu,
persistent Dislike, link-only Jam invitations and Discover tab. Stable remains
0.2.1/code 12. Future canaries remain internal until explicitly released.

- The final optimized APK updates the connected Galaxy S22 in place. The signed-in
  Home and real full-screen player remain available; the artwork-colored menu,
  its secondary page, dismissal and Discover navigation were inspected. Existing
  account data, full-screen/moving-art preferences and reduced animation were
  preserved. Playback was left paused. No real tracks were rated during this check.
- 1,435 app/shared unit checks passed, with one optional live lyrics test skipped.
  All 29 backend tests passed using the exact staged source, excluding unrelated
  landing-page work. Signed packages retain their baseline profiles and Dev signer.
- Native menu checks pass in Light, Dark and Pink Clouding. The fixture includes
  the portrait player's real modal window: an initial activity-owned popup was
  covered by that window, caught on the S22 and replaced with a transparent dialog.
  Primary Dislike, nested navigation, Share/Add to playlist callbacks, Sleep timer
  and dismissal pass with the corrected window layering.
- Discover navigation and its expanded/collapsed bar pass all six combinations
  of the three themes and glass enabled/disabled.
- A real PlaybackService/ExoPlayer test with short local PCM fixtures confirms
  persistent dislikes, automatic queue pruning, immediate current-song skip,
  explicit selection/queue exceptions, blocked repeat and exhausted-queue stop.
  It runs only on the disposable emulator, not against the owner's ratings.
- Final native regressions cover full liked-list metadata, played Spotify shelves,
  full-screen Automix artwork and a cached moving cover before/after the dissolve.
  Canary 9's controlled S22 scrolling captures remain documented in PERFORMANCE.md;
  this release does not claim the compact-menu changes were a new performance trial.

Release assets are the universal `daylight-dev.apk` and its checksum. Arm64 remains
available locally for manual installation. A Canary needs one manual Dev.8 install
without uninstalling; subsequent public Dev updates use the normal update popup.

---

# Previous candidate: Canary 11 (historical)

**0.2.2-canary.11 · Android code 30 · internal testing only**

Optimized arm64 and universal APKs update Daylight Dev in place with its existing
package and signer. Public Dev.7/code 27 and stable 0.2.1/code 12 remain unchanged.
No app release, tag or source push was made. The larger Jam queue backend is
live at jam.sh1vvy.com; its existing landing page and deployed assets were preserved.

## Changes

- Automix uses a restrained slow glow shared by the Mixing label and progress
  fill. The bar retains the actual playhead position. The two covers dissolve
  around the audio engine's metadata handoff, normally over about 1.4 seconds.
  Existing full-screen/card geometry and audio mixing are preserved. Animated
  artwork plays around the brief dissolve, and resumes on the incoming cover.
  Missing art keeps a usable image; Reduce animation uses the current cover
  immediately. Pausing freezes cover progress and settles the glow.
- Glow state is read during drawing and the cover fraction in graphics layers.
  Engine ticks are interpolated on the frame clock; progress does not continually
  recompose the player root. One glow clock stops when paused, backgrounded or
  outside a mix. No additional audio analysis, video pair or blur pass is added.
- Jams share up to 2,000 upcoming songs plus the current track and 100 previous
  tracks. Manual queue priority, occurrence IDs and next/previous are retained.
  Server queues persist atomically in bounded chunks; routine playback state
  writes avoid rewriting the full list.
- Add to playlist uses YouTube's authenticated editable-playlist dialog, omitting
  saved playlists that cannot be edited, built-in lists and Spotify collections.
  A verified-ownership fallback is used only if that dialog is unavailable.
  Choices use the Library's newest-created/recently-added activity order.
- The main-player action sheet retains the track's color wash with rounded action
  groups, softer icon tiles and consistent padding. It opens fully expanded;
  its lower actions remain scrollable. The heart/menu gap is slightly smaller,
  retaining the existing touch targets and callbacks.
- Canary 9 scrolling work and Canary 10 liked/Spotify metadata caching remain.

## Validation and remaining phone checks

**1,430 app/shared checks passed**, with one optional live lyrics test skipped.
Five final native emulator cases passed, covering all three themes, a cached
moving cover, and the glow clock. S22 themed previews and its real editable-list
check also passed. Both packaged APKs retain their Dev signer, licenses, baseline
profiles and non-debuggable runtime. The installed S22 APK hash matches arm64.
Playback and synced lyrics worked on the optimized phone build; playback is
left paused. Its existing Reduce animation preference remains enabled.

The current QA report accompanies the APKs. Automated player fixtures use offline
covers and engine-shaped progress, rather than claiming an audible two-song mix.
They cover the dissolve, stable geometry, reduced motion, themed menu callbacks
and access to its last action. Separate clock checks cover a breathing glow,
pause settling and no continuing paused redraws. Motion-art checks use a locally
cached silent clip on the emulator.

The connected S22's real signed-in add-to-playlist dialog returned 14 editable
choices; three were independently checked against playlist ownership. The test
made no playlist or account changes. Visual previews on the physical phone now
use in-memory settings only. Full-screen and animated cover art were restored
through the normal settings UI after the first preview persisted its choices.

The deployed Jam backend passed a disposable-room test with two authenticated
clients and a full 1,727-track queue, including next, clock sync and host controls.
Chunk storage also has restart/hibernation, failed-write, migration and large-value
coverage. This is a capacity/regression check, not a 50-room load benchmark.

For your listening test, play two queued songs with Automix enabled, on supported
normal/lossless output. Let the planned transition occur naturally and compare
its sound, title handoff, slow glow and cover dissolve with the reference video.
Try a pair with animated covers, a missing cover, pause/resume during a mix, and
Landscape. Hi-res Automix restrictions, Jams and ordinary crossfade still use
existing policies. Test ordinary playback and the duplicate-playlist prompt too.

AirPlay is not included in this candidate. Mac, Apple TV and HomePod are the
agreed future receiver targets; an Android sender transport and real-receiver
validation remain separate work. Store-specific packaging also remains future
work, including a Google-Cast-free flavor for F-Droid consideration.

---

# Previous candidate: Canary 10 (historical)

**0.2.2-canary.10 · Android code 29 · internal testing only**

The arm64 APK is installed over Canary 9 on the connected Galaxy S22, retaining
its account, library and settings. Optimized arm64 and universal APKs use the
existing Dev package and signer. Public Dev.7 and stable 0.2.1 remain unchanged;
nothing was pushed, tagged, deployed or published.

## Changes

- Liked songs keeps the full catalogue list across app restarts. The saved list,
  artwork and creator render immediately while a stale list refreshes in the
  background. Partial or failed refreshes keep the last complete list usable.
  Unlikes remove rows immediately, and Library refresh forces a fresh check.
- Spotify playlists retain their rows and YouTube matches. Reopening uses the
  saved list; refreshing reuses matches by Spotify recording ID rather than
  repeating searches for every song. New recordings are matched as needed.
- Actually playing a cached Spotify playlist adds it to Library → Playlists and
  Home → Listen again, with its cover, title and creator. Opening alone does not
  count as listening. Show all and ordinary playlist actions use these cards.
- Only metadata is saved; this does not download liked songs or playlist audio.
  IO workers handle parsing and atomic writes. Disk use is bounded to 24 MiB,
  with 8 MiB/24 whole collections per identity and two warm identities in memory.
  Cache entries are isolated by Google account/profile and Spotify session.
- Existing UI, cover transitions, blur, Glass, playback sources, downloads and
  audio-cache controls remain as before. Canary 9's performance work is retained.

## Checks

- **1,425 app/shared checks passed**; one optional live lyrics check skipped.
- **Three targeted native emulator tests passed**: full liked-list readiness,
  warm list pagination/unlike consistency, Spotify fresh-cache reuse and
  actual-play shelf insertion, identity separation, and the existing two-by-two
  Library preview/Show all behavior. Spotify cases use offline catalogue fixtures
  and local silent test audio, not a live Spotify session.
- The optimized S22 build loaded **1,727 liked songs**, then restored the same
  full list after its app process was stopped and restarted. Sign-in remained,
  the existing queue remained available and playback was left paused.
- One S22 cached-list scroll smoke pass rendered 647 frames, with 23 missed
  deadlines (3.55%). This is a regression smoke check, not a new multi-device
  benchmark or a controlled comparison with Canary 9.
- Both APKs retain `com.sh1vvy.daylight.dev`, code 29, the existing Dev signing
  certificate, a non-debuggable runtime, bundled licenses and baseline profiles.

## Phone checks

- Open Liked songs, leave, return and reopen after restarting the app. Refresh
  Library after changing likes on another device. Cached rows should stay usable.
- Play a Spotify playlist, return to Library and Home, then reopen its card.
  Check its title/cover and playback. New Spotify entries may still need matching;
  cached recording matches should not be searched again on every visit.
- Switch Google profiles and reconnect/disconnect Spotify. Each identity should
  see its own saved rows; disconnected Spotify cards should disappear.
- Keep testing scroll feel on other Android phones. The implementation is general;
  physical measurements so far are from the connected S22.

---

# Previous candidate: Canary 9 (historical)

**0.2.2-canary.9 · Android code 28 · internal testing only**

Install over Daylight Dev.7 or an earlier canary. Both APKs retain the Dev
package and existing signing certificate. The arm64 APK has been installed on
the connected Galaxy S22 without clearing its account, library or settings.
Nothing was pushed, tagged, deployed or published; public Dev.7 and stable 0.2.1
remain unchanged.

## Changes

- Optimized phone build: release R8 and non-debuggable runtime, while preserving
  in-place Dev updates. CI also builds this optimized variant for future artifacts.
- Less work during scrolling: detail artwork/blur offsets update during
  placement, moving cover corners update during drawing, and Library retains
  playlist transformations while playback/chrome changes.
- A bounded Android performance profile uses UI methods actually exercised on
  the S22, replacing broad whole-app startup rules. Library profiles remain.
- Softer update popup: rounded themed card, butterfly, eased entrance, clear
  download/install button, readable Markdown notes and visible progress.
  Existing download/cancel/retry/install and dismissal behavior remain.
- App layout, feature set, blur, glass, artwork resolution, queue warming,
  language filtering, playlist order and animation timings remain as before.

## Checks

- **1,418 app/shared checks passed**; one optional live lyrics check skipped.
- **13 native emulator cases passed**, covering existing Home/Library cover
  transitions in Dark, Light, Pink Clouding, Glass and Reduce animation,
  library preview/order, mix playback/refills, profile/search, album/artist
  destinations, lyric motion, and update popup states in all three themes.
- Real S22 optimized runtime opens Home and Liked songs, retains sign-in,
  plays the existing track and opens synced lyrics. Playback is paused again.
- Three comparable Liked songs scroll passes: median missed frame deadlines
  **16.03% → 2.41%**. Home optimized passes are around 4%. See
  [performance notes](PERFORMANCE.md) for raw counts, conditions and limits.
- APK package `com.sh1vvy.daylight.dev`, code 28, non-debuggable, original Dev
  signer, bundled licenses and Android baseline profile verified. Checksums
  accompany arm64 and universal files. Native fixtures use debug APKs; phone
  performance measurements use the packaged optimized APK.

## Phone checks

- Scroll Home, Library and a large playlist; compare the feel with Dev.7.
  Check normal and Glass modes in Dark, Light and Pink Clouding.
- Open and close an album/playlist from Home, Library and Show all. Check rounded
  moving corners, blur, foreground title, delayed video and restored position.
- Try playback, lyrics, search, Play my mix and an extended listening session.
  Check both a warm cache and a new track/page.
- The update popup is exercised with offline fixtures because internal canaries
  never offer public updates. Preview images use a sample Dev.8 notice; no such
  release was created. Public update checks resume when a Dev version is requested.
- Repeat scrolling on other phones, including Oppo Find X9. The current physical
  measurements establish S22 improvement, not results for unconnected hardware.

---

# Previous candidate: Canary 8 (historical)

**0.2.2-canary.8 · Android code 26 · internal testing only**

Install over the existing Daylight Dev/Canary app. Its package and signing key
are unchanged; no public release, website or download badge was published.

## Changes

- Album pages end with **More by the artist** and **Similar Albums for you**,
  using horizontal square album cards with titles below. The current album,
  duplicate endpoints and repeated albums across the two rows are excluded.
  Similar albums use the catalogue's related releases, with track-radio album
  metadata as a fallback. Empty/unavailable catalogue sections are omitted.
- Playlist pages end with **Featured artists**: circular portraits and names,
  scrolling horizontally. Collaborators with credited channel links are included
  once, in playlist order. Tapping a portrait opens that artist's profile.
  Unknown artist identities are not guessed from a combined credit line.
- The opening cover's page blur, tonal fade and title are layered above the
  moving artwork. The fade continues through the page body, preventing its
  sharp square edge from crossing over the title/controls just before the
  transition settles. The softened artwork foot is restored, respecting
  Reduce blur and Android availability. Animated video still waits for the
  opening to finish. Closing uses the existing page transition.
- **Appearance → Recommendation languages** offers a searchable, multi-select
  list of languages to hide from recommendations. All are allowed by default.
  Save applies choices; Cancel discards edits; Show all languages resets them.
  The sheet stays usable with the keyboard open.
- Language choices affect Home discoveries, Quick picks and fresh tracks in
  Play my mix. Saved songs, history, manual playback and search remain usable.
  Filtering uses explicit catalogue labels and language metadata already learned
  from lyrics translation; it adds no per-track language lookup. Unlabelled
  recordings can remain in recommendations. Artist nationality and ambiguous
  scripts are not treated as a recording's language.
- Quick picks always supplements the feed with a recent-track recommendation
  seed when one is available, and gives those discoveries priority over generic
  feed picks. A populated generic feed no longer prevents this enrichment.

## Resource limits

Discovery loads near the end of the detail list instead of delaying page opening.
Artist header/discography previews are coalesced and retained for ten minutes,
with at most 32 entries and 640 weighted catalogue records. Only composed
playlist portraits request their headers; album rows contain at most 20 cards.
Related track radio reuses the existing four-seed/96-track cache. Language hints
hold at most 1,024 identities and reset when the listener/profile scope changes.

## Verification

- **1,418 app/shared tests passed**, with one optional live lyrics check skipped.
- **9 native Android checks passed** across cover transitions in Dark, Light,
  Pink Clouding and Reduce animation, Home quick-picks/mix regression checks,
  the keyboard-aware language picker, and album/artist row scrolling/navigation.
- Cover checks inspect frozen intermediate frames and require the release title
  to be visible above the artwork before the shared cover handoff finishes.
- Catalogue row tests use warmed offline fixtures, including collaborators and
  repeated artist credits. Live account recommendations and artist portraits
  still need the phone's connected-source test.
- Both APKs retain package `com.sh1vvy.daylight.dev`, version code 26 and the
  existing development signer. The universal APK includes arm64, ARMv7, x86 and x86_64.

## Phone checks

- Open an album and scroll past its tracks: check both rows, browse them
  horizontally and open an album card. Repeat with an artist with a small
  discography and while offline.
- Open a playlist with collaborations: check portraits, names and profile
  destinations. A missing portrait should show the person placeholder.
- Open playlists/albums from Home and Library; check the first rounded frame,
  bottom blur and uninterrupted title/controls near the end of opening.
- Hide one or more recommendation languages, refresh Home and try Play my mix.
  Check that saved songs and manual search still work. Unknown recording
  languages can pass through; this is metadata-based filtering.

---

# Previous candidate: Canary 7 (historical)

**0.2.2-canary.7 · Android code 25 · internal testing only**

Install over the existing Daylight Dev/Canary app without uninstalling. This
candidate keeps its package and signer; stable Daylight can stay alongside it.
Public releases, downloads and websites have not been changed.

## Changes in this candidate

- Home uses a compact butterfly on the left and Daylight wordmark on the right,
  with matching page gutters. A small Sign in button sits beside the guest
  profile icon; the large sign-in card and extra descriptions are removed.
  Empty shelves are omitted. Quick picks blends recent tracks
  with discoveries, aiming for one familiar track and two recommendations per
  three-row page, with duplicates removed.
- **Play my mix** appears below Quick picks only when signed in, and starts
  immediately from already loaded favorites/library and
  discovery tracks. The continuing mix aims for two familiar songs per new
  discovery. It also samples saved YouTube playlists, permitted local files and
  playable songs from local/Spotify-imported playlists in the background.
  Discovery uses YouTube Music recommendations; this does not add a Spotify
  listening-history or recommendation API integration.
- Starting the mix enables AutoPlay and turns repeat off. Turning AutoPlay off
  stops its refill; manually playing something else takes over. Recent songs,
  queued tracks and duplicate recordings are avoided while alternatives exist.
  A small offline library can repeat after completing its available songs.
- Offline mixing filters out unavailable streams and uses local files, verified
  downloads and fully cached canonical tracks. It makes no recommendation or
  playlist requests while offline. Jams exclude private files and retain the
  existing shared radio behavior; personalized service refills are local only.
- Cover opening keeps the tapped card's rounded corners and gradually opens into
  the header. Only the sharp still artwork moves; the page fade stays behind it
  and animated artwork waits for the transition to finish. The old blurred
  artwork foot is removed. Late Home/Library feed updates cannot rearrange the
  source cards during opening.

## Resource limits

The shared familiar pool holds at most 600 tracks, including a reserved local
sample. Warming is coalesced and limited to once per five minutes, sampling two
saved playlists' first pages per pass. Initial mixing reuses loaded data when
available. Recommendation responses have a ten-minute cache capped at four
seeds/96 tracks. Refills append at most 12 tracks, start only when fewer than
eight remain, and retain 30 played queue entries after the history grows. These
limits keep an endless listening session from becoming an ever-growing queue.

## Canary 7 phone checks

- Install over your current canary, open Home, refresh and check the compact
  heading, compact Sign in beside the guest profile, mixed Quick picks and lack
  of repeated descriptions. Play my mix should stay hidden until sign-in and
  appear below Quick picks afterwards.
- Tap Play my mix with liked songs and saved playlists available. Check prompt
  first playback, skip through several tracks and confirm the queue refills.
  Turn AutoPlay off, then manually choose another track; neither should be
  overridden by a late recommendation.
- Test an offline session with downloads/local files. A small available pool
  may repeat; unavailable streaming tracks should not occupy the queue.
- Open albums and playlists from Home, Library and Show all. Watch the first
  rounded frame, the moving cover, sharp settled art and delayed animated cover.
  Return to the same shelf position. Repeat in Light, Dark, Pink Clouding and
  with Reduce animation enabled.

## Canary 7 verification

- **1,407 app/shared tests passed**, with one optional live lyrics check skipped.
- **11 native Android checks passed**: four in Dark, three in Light, three in
  Pink Clouding and one with Reduce animation. Guest sign-in/profile sizing,
  matching brand gutters, mixed Quick picks and the signed-in-only mix placement
  are checked on the real Home screen. Silent local audio exercises first play,
  service refills, repeat reset, AutoPlay off and manual queue takeover.
- Cover checks inspect rounded early frames and complete, centered artwork
  while moving. Home/Library opening and return, late feed changes, deferred
  video, Show all and restored viewport pass. Native screenshots were inspected.
- Both APKs have package `com.sh1vvy.daylight.dev`, version
  `0.2.2-canary.7`, code `25`, the existing Dev certificate and bundled licenses.
  Checksums accompany the APKs. Nothing was pushed or published.

Personalized recommendations with a real signed-in account, physical-phone
animation feel and network artwork remain manual checks. Emulator fixtures use
private test files, cached silent video and simulated signed-in UI state;
no real account credentials are installed.


## Earlier candidate: Canary 6

**0.2.2-canary.6 · Android code 24 · internal testing only**

Install the universal canary APK over Daylight Dev/Canary without uninstalling.
It keeps the existing Dev package and signing key. Stable Daylight can remain
installed alongside it. This candidate has not been published as a release.

## Changes in this candidate

- Playlists preview four compact covers in two rows and two columns. A short
  extra column provides two more covers; Show all opens the full collection.
  Recent activity order, pins, import actions and shared cover motion remain.
- Long-press an owned playlist or use its overflow menu to edit its name and
  choose/reset its cover. Local edits work without sign-in. YouTube names sync
  to the owning account; custom covers stay on this device, scoped to that
  account/profile. Imported photos are copied privately and decoded to at most
  1024px on a background worker. Cancelling leaves the playlist unchanged.
- Home shows Quick picks as three spacious song rows per page, reusing loaded
  recommendations and recent tracks without another request. Duplicate tracks
  are removed within the preview. Recent albums/playlists remain available in
  Back in rotation. Late Recents results do not shift the Quick picks section.
- Tap album artwork on the player or a playlist/album page to view it in full
  screen. Pinch or double-tap to zoom; Close/Back returns to the same page.
- Lyrics use a spring trail adapted from Accompanist Lyrics UI. The anchor keeps
  the existing lyric timing; up to five following rows catch up with bounded
  motion, driven by a single frame loop. User scrolling, hidden panels and
  Reduce animation clear the added motion. Translation, romanization, lyric
  sharing and tap-to-seek keep using the existing renderer.

## Lyrics reference

The supplied `config (1).json` matches the Accompanist desktop viewer’s config
fields and its `C:/Users/Simon/Music/Local` example path. It configures a desktop
window; it does not contain lyric provider URLs, lyrics or word timing.

Source: <https://github.com/6xingyv/accompanist-lyrics-ui>, inspected at commit
`9cee275e04e789ab93ed5635a591176a15251053`. Daylight adapts the spring-chain
motion into its current Android renderer instead of replacing the whole engine
or changing its pinned Compose/Kotlin toolchain. The source notice and full
Apache-2.0 license are included, along with the app’s GPLv3 license.

## Canary 6 verification

- App/shared suites passed **1,392 tests**, with one optional live lyrics check skipped.
- **12 native Android checks passed**: the four-cover preview, Show all/return,
  Quick picks paging and late Recents stability, and playlist edit/cancel/cover
  persistence/full-screen artwork in Dark, Light and Pink Clouding; plus Home
  and Library cover opening/return and the real lyric renderer’s intermediate
  motion, translations, tap-to-seek and reduced-motion placement.
- Saved photo tests use a 2400×1800 input, verify the 1024px bound, remove the
  source/draft, reload the store, then replace/reset and verify old copies are
  deleted. Editor Save remains visible above the keyboard.
- Native screenshots were inspected. Full-resolution network artwork and exact
  animation feel on a physical phone remain manual checks.
- Package `com.sh1vvy.daylight.dev`, version `0.2.2-canary.6`, code `24`; the Dev
  certificate matches earlier canaries. GPLv3 and Accompanist’s Apache-2.0
  license/source notice were verified inside the APK.

## Canary 6 phone checks

- Play a word-synced song and watch several line changes, including a short
  instrumental gap. Seek backwards, scroll manually, translate/romanize, and
  return to automatic following. Repeat with Reduce animation.
- Check the four-cover preview with music playing; swipe the small extra column,
  use Show all, then return to the same shelf position.
- Rename an owned YouTube playlist and a local playlist. Pick a large photo,
  cancel once, save once, restart the app and check that the cover persists.
  Reset cover restores the provider/default image. Check keyboard fitment.
- Refresh Home and check Quick picks stays in place while Recents finishes.
- Tap artwork on the main player and on an album/playlist; pinch, double-tap,
  close and press Back. Playback should continue unchanged.

## Changes carried forward from Canary 5

Canary 5 added directional tab/page motion, softer settings expansion, a compact
Replay shortcut and the fixed 2×2 Record widget. Its oversized playlist grid is
replaced by the compact four-cover preview above. The website starts in Dark;
Light and Pink Clouding still theme the whole page. Public app download links
and releases remain unchanged.

## Canary 5 verification

- The final app/shared suites passed **1,383 tests**, with one optional live lyrics
  check skipped. Regression checks include canonical/raw/localized Liked Music
  filtering while preserving user playlists and ordinary provider duplicates.
- The native navigation container was measured at intermediate frames: tabs travel
  continuously in either direction, settle at the correct position, and skip the
  slide when Reduce animation is enabled.
- Real Library grid checks exercise 24 remote playlists plus one local creation:
  four visible columns/four rows, duplicate likes removal, one Replay entry,
  header actions, Spotify import menu, horizontal paging, local/remote Show all,
  retained horizontal position after Back and the new-playlist sheet.
- Native Dark checks passed for Home shared covers and deferred animated artwork,
  both Library layouts and Back, liked-song pagination/removal, and profile,
  accounts/settings, category expansion/collapse and search navigation.
- The grid and Library cover/Back checks passed in Light with glass enabled and
  Pink Clouding with glass off. Settled screens were inspected in all three
  themes. Dark with glass and Reduce animation also passed.
- Record RemoteViews passed at 110×110, 130×220, 160×160, 260×110 and larger host
  dimensions, including font scale 2 and light/dark colours. Play/pause and title
  bounds remain inside the card. Loaded circular artwork stays centered, guest
  Jam controls remain locked, and unavailable skips remain disabled. Installed
  provider metadata reports targetCellWidth/Height 2 with RESIZE_NONE.
- Website tests passed all **23 checks**. Cloudflare deployment succeeded; the
  live browser reload shows a dark background, Dark selected and theme-dark.webp.
- The manual universal APK is `com.sh1vvy.daylight.dev`, version
  `0.2.2-canary.5`, code **23**, with the existing Dev signing certificate:
  `6e673ee0dfe35a4dc279c50b4548f1550d4fdb915e7b2329ef3475339ad51b37`.
  No app release or Git push was made.

## Canary 4 changes carried forward

This candidate fixes abrupt cover opening and extends it to Home. Visible cards
register their bounds before tapping; the selected sleeve grows from that position
without also scaling the whole page. Cached card artwork remains during navigation;
larger artwork and animated-cover playback wait until it finishes. Back retains
the existing closing motion and shelf/grid positions. Reduce animation still
changes pages immediately. The test-only Compose clock dependency measures actual
intermediate frames and is not included in the application APK.

Library now has a Liked songs shortcut beside Downloads and the other folders. It opens
the connected YouTube Music account's liked songs using the existing bounded,
account/language-scoped cache. Remaining pages still load normally, and unliking
removes the song from the open list. Guests are offered sign-in. Podcast shows,
episodes and their Home shelves are filtered out; the dedicated podcast Library
request and an unused added-tracks feed are removed.

With Liquid Glass off, the bottom tabs use a gentle sliding selection capsule
at its normal size. The glass lift/squash animation is only used with Liquid
Glass enabled. Both modes retain tab swiping and the collapsing mini player;
Reduce animation makes regular tab selection immediate.

## Canary 4 verification

- The final Android/shared suites passed 1,380 tests; one optional live lyrics
  test was skipped. Eight new regression checks cover initial/continued Home
  feeds, mixed/localized podcast types, grid/list libraries, search, playlists
  and radio while keeping ordinary music, music videos and uncatalogued uploads.
- Native Android checks passed for Library rows/grid and Home hero, Recents
  list/grid and square cards. Frozen frames assert growth from the source card.
  An offline animated cover stays unmounted during opening and plays afterward;
  its settled frame was inspected.
- Library navigation passed in Dark with glass off, Light with glass on, Pink
  Clouding with glass off, and Dark with glass on and Reduce animation enabled.
  The final APK also passed Home transitions and Liked songs pagination/removal.
- The real Liked songs shortcut opens the offline liked fixture and its continuation.
  The warm first page is available synchronously; removing a like updates both
  the open collection and Library data. Back returns to the shortcuts.
- Native tab checks measure regular selection at rest and at two moving frames:
  width and height remain constant while its position changes. Glass still lifts
  its lens; switching it off mid-flight returns to regular geometry. Tab swiping,
  Search/return, both collapsed layouts and immediate reduced-motion selection pass.
- The universal APK is `com.sh1vvy.daylight.dev`, version `0.2.2-canary.4`, code
  22. Its existing Dev signing certificate is SHA-256
  `6e673ee0dfe35a4dc279c50b4548f1550d4fdb915e7b2329ef3475339ad51b37`.
  It has not been published or pushed as an app release.

## Canary 3 verification carried forward

- Universal APK and native test APK built successfully. The final Android/shared
  suites passed 1,372 tests; one optional live lyrics test was skipped.
- Actual Android Library navigation passed with offline playlists in Dark with
  glass off, Light with glass on, Pink Clouding with glass off, and Dark with
  glass on and Reduce animation enabled. These checks cover shelf/grid opening,
  different cards, scrolling past the cover, Back destinations and retained shelf
  positions. Intermediate frames retain artwork; settled screens were inspected.
- The existing native profile-menu, account/settings navigation and search
  editing/source-switch checks also passed with the new navigation wrapper.
- Package `com.sh1vvy.daylight.dev`, version code 21 and the existing Dev signer
  were verified. This build has not been published as an app release.
- The lyrics video and corrected phone proportions are live at
  <https://daylight.sh1vvy.com/#lyrics>. All 23 landing-page tests passed. The three
  preview clips start on scroll, pause offscreen and honor reduced motion.

## Library phone checks

- Verify Liked songs appears once, and system Liked Music is absent from Playlists.
- Swipe the two-row Playlists preview horizontally; open Show all and return to
  the same position. New playlists and recent song additions should remain first.
- Use + for New playlist and the Playlists menu for Import Spotify.
- Open Your Replay and verify its full listening story remains available.
- Remove/re-add the Record widget and confirm it occupies a fixed 2×2 placement;
  test with your phone's font size and launcher, playing and paused.

- Open an album or playlist from a Library shelf and return with Android Back.
- Scroll a shelf sideways, open a card, and check that Back returns to the same position.
- Use Show all, scroll the grid, open different playlists and return to the grid.
- Open a playlist, scroll well past its cover, then go Back; check that the cover
  does not fly in from outside the screen.
- Quickly open/close/reopen cards; check for duplicate pages, blank covers or flickering.
- Repeat with Liquid Glass on/off, Light/Dark/Pink Clouding and Reduce animation.
- Switch Home/Explore/Library with Liquid Glass off and check that the highlight
  slides without swelling. Swipe tabs, open Search and return; scroll to collapse
  the player. Repeat with glass enabled and toggle it while the highlight moves.
- Check real-network and cached artwork on the phone; emulator motion checks are
  useful regressions but do not establish smoothness on every physical device.

## Changes carried forward from Canary 2

This candidate removes legacy mesh controls, WebDAV/SMB libraries and the JioSaavn
client. KuGou and BetterLyrics Portato are off by default but can still be enabled
manually. The current artwork treatment, streaming, lossless quality tiers,
downloads and device music remain supported. Home uses Shivvy’s supplied wordmark.
The butterfly remains beside the wordmark. Both Home and the Jam website now
use smooth vector lettering. Search controls are rounded, recent requests are
reused within bounded caches, and the profile menu follows the selected theme.
The new Record widget adds a cropped disc, centered track details and native
playback controls. Jam retains the existing public app downloads.

## Canary 2 verification carried forward

- Final APK built successfully; 1,372 Android/shared tests passed, with one optional
  live lyrics test skipped. All 25 Jam website tests passed.
- Canary 2 installed over Canary 1 with the same Dev package and signer on an
  Android 15 emulator. Prior Canary 1 checks covered Dev.6 upgrades, preference
  cleanup and encrypted credential migration. No crashes were recorded.
- Four native tests passed: actual widget inflation, loaded artwork, large text,
  Jam restrictions, profile-menu navigation/dismissal and search source/editing
  behavior. Profile/search checks also passed in Light and Pink Clouding. The widget
  was inspected in Light and Dark at compact and large sizes.
- Home’s vector lettering was inspected on Android. The configured Last.fm
  dialog now offers browser sign-in; the owner's credentials passed a real
  signed API token request.
- The wordmark update is deployed at <https://jam.sh1vvy.com>. The page uses the
  new assets and keeps its stable 0.2.1 and public Dev.6 download links.
- Real Last.fm account authorization/scrobbling and phone playback checks still
  need manual device testing. The developer credentials are already configured.

## Phone checks

- Confirm the canary installs over Dev.6 and your playlists and saved settings remain.
- Check Home in Light, Dark and Pink Clouding, then refresh and scroll it.
- Check that the retired player setting and network-library entries are absent.
- Check lyrics-provider defaults, and manually enable/disable the two opt-in providers.
- Play a normal stream, then Lossless and Hi-Res Lossless on Wi-Fi. Check the actual
  decoder in Stats for nerds and the static lossless badge where applicable.
- Skip forward/backward, seek, pause/resume, and confirm the warmed queue stays responsive.
- Play an existing download and a device file with airplane mode on.
- Join a Jam, play a playlist, and check its shared queue.
- Open Spotify playlists and navigate back through the main tabs.
- Open Last.fm in Account & integrations, choose Connect, approve Daylight in
  the external browser, then return. Verify the username, Now Playing and a
  qualifying scrobble, then pause/resume and skip tracks. Disconnect must clear
  the session. The owner's credentials have passed a signed API token check;
  real-account authorization is a manual phone check.
- Add **Daylight · Record** from the Android widget picker. Test sizing,
  play/pause/previous/next, stopped playback recovery and Jam guest controls.
- Open and dismiss the profile menu with Back, outside tap and its close button;
  check all themes and Reduce animation. Search, clear the field, switch sources
  and filters, backspace and return to a recent query.

## Last.fm setup

Register one Daylight API application at <https://www.last.fm/api/account/create>.
Use **Daylight** as the application name and <https://sh1vvy.com> as its website.
Describe it as “An Android music player with optional Last.fm scrobbling and now playing.”
The browser token flow does not require a callback URL; leave that optional field blank.

Add the API key and shared secret to the ignored root `local.properties` file:

```properties
LASTFM_API_KEY=your_application_api_key
LASTFM_SECRET=your_application_shared_secret
```

Do not commit this file or paste credentials into public chat, issues or releases.
Rebuild the canary after adding them. Everyone using the resulting app connects
through their browser and approves Daylight; individual API registrations are unnecessary.
After approval, return to Daylight; “Finish connecting” is available if automatic
completion has not happened. Scrobbling and now playing turn on upon connection,
and can be turned off through Accounts & integrations.

A track qualifies after half its length or four minutes, whichever comes first,
and must exceed 30 seconds. Pauses, buffering and seeks do not create extra
listening time. Repeats are distinct listens. Disconnect clears the encrypted
session and cancels pending submissions. Daylight’s application credentials are
configured locally and validated; real-account authorization still needs the
manual phone check.
