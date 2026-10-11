<!-- DAYLIGHT-ATTRIBUTION
Daylight modifications by sh1vvy.
Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
Daylight changes: 2026-10-11 through 2026-10-11.
Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
Original authors' notices and licenses remain applicable to their material.
-->

# Daylight library core

An independently implemented metadata-storage and refresh layer introduced in
Canary 13. This module has no Android, Compose, player, network-client or inherited
app-model dependency. The app and its transitional adapters still contain
GPL-covered upstream material; this module does not establish independence of
the entire application.

## Behaviour

- Partition catalogue metadata by account/profile and collection. Hash file names;
  exclude credentials, audio, transport state and continuation tokens.
- Serve warm reads from bounded memory. Perform disk access, encoding, decoding
  and eviction off the caller's thread. Bound row counts and entry counts.
- Update one collection without serializing other collections. Skip identical
  writes; atomically replace complete, checksummed entries.
- Share simultaneous refreshes. A disappearing screen does not cancel another
  screen's work; identity changes cancel obsolete requests. Failed sections
  preserve their previous usable data, while verified empty sections clear it.
- Evict whole entries. Reject an oversized entry without replacing its previous
  copy. Isolate damaged and unknown records.

## Integration

`CollectionMetadataCache` is a compatibility adapter for existing models. It
migrates JSON bundles on demand and retains a legacy bundle if migration cannot
preserve a whole entry. Records use 24 MiB on disk, an 8 MiB per-entry ceiling,
at most 96 disk entries, and 12,000 warm rows/32 entries. Audio caching is separate.
Liked-song metadata has retention priority over ordinary collections, while the
same hard memory and disk limits continue to apply.

`LibraryOverviewStore` bridges the new section/card model to the current UI.
Shelves are cached separately from full liked-song lists, using 4 MiB on disk
and at most 4,000 warm cards/four overviews. Account/profile and display language
partition the overview. Fresh overviews and complete liked lists can reopen
without a provider fetch for one minute; explicit refresh bypasses freshness.
Local membership edits invalidate freshness and obsolete provider work.

Existing UI, playlist operations, liked pagination, Spotify resolution, cover
animations, playback, Jams and audio preloading remain in their current layers.
Future stages can replace the adapters and domain/repository layer separately.

## Provenance and verification

The new core was implemented from these behaviour requirements. Research used
architectural guidance, without importing another player's source:

- [Android offline-first guidance](https://developer.android.com/topic/architecture/data-layer/offline-first)
- [Kotlin concurrency guidance](https://kotlinlang.org/docs/shared-mutable-state-and-concurrency.html)

Original GPL and separately licensed components remain unchanged. Scoped Daylight
notices/hashes are maintained in the root contribution inventory. This record
identifies provenance; it does not claim exclusive rights over standard caching
patterns, functionality or other non-copyrightable material.

Run `./gradlew :libraryCore:test` for targeted IO, restart, budgets, corruption,
concurrency and refresh cancellation. The app suite covers migration and bridges.
