/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.home

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryRepositoryTest {
    @Test fun seedsPreferDifferentArtistsWithoutDroppingSameArtistFallbacks() {
        val seeds = listOf(DiscoverySeed("a", "Artist"), DiscoverySeed("b", "artist"), DiscoverySeed("c", "Other"), DiscoverySeed("d", "Third"))
        assertEquals(listOf("a", "c", "d"), DiscoveryRepository.selectSeeds(seeds).map { it.id })
        assertEquals(listOf("a", "b"), DiscoveryRepository.selectSeeds(seeds.take(2) + seeds.first()).map { it.id })
    }

    @Test fun poolsMixInRoundsExcludeHeardAndReuseFreshResultsWithinTheirOwnAccount() = runTest {
        var calls = 0
        val repo = DiscoveryRepository<String, String>(this, fetch = { _, seed -> calls++; listOf("heard", "$seed-1", "shared", "$seed-2") }, id = { it }, now = { 90_000 })
        val seeds = listOf(DiscoverySeed("a", "A"), DiscoverySeed("b", "B"))
        val expected = listOf("a-1", "b-1", "shared", "b-2", "a-2")
        assertEquals(expected, repo.discover("one", seeds, setOf("heard")))
        assertEquals(expected.take(3), repo.discover("one", seeds, setOf("heard"), 3)); assertEquals(2, calls)
        repo.discover("two", seeds, emptySet()); assertEquals(4, calls)
        repo.clear(); repo.discover("one", seeds, emptySet()); assertEquals(6, calls)
    }

    @Test fun discoveryUsesAtMostTwoFetchesAtOnceAndAFailedSeedDoesNotCancelOthers() = runTest {
        val owner = CoroutineScope(coroutineContext + SupervisorJob())
        try {
            var active = 0; var peak = 0
            val repo = DiscoveryRepository<String, String>(owner, fetch = { _, seed ->
                active++; peak = maxOf(peak, active)
                try { delay(100); if (seed == "a") error("Offline") else listOf(seed) } finally { active-- }
            }, id = { it })
            val result = async { repo.discover("one", listOf(DiscoverySeed("a", "A"), DiscoverySeed("b", "B"), DiscoverySeed("c", "C")), emptySet()) }
            advanceUntilIdle()
            assertEquals(listOf("b", "c"), result.await()); assertEquals(2, peak); assertEquals(0, active)
        } finally { owner.cancel() }
    }
}
