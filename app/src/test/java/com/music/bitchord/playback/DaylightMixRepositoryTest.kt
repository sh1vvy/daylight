package com.music.bitchord.playback

import com.music.bitchord.data.LikeState
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.LikeStatus
import com.music.bitchord.data.model.Song
import org.junit.After
import org.junit.Test
import org.junit.Assert.*

class DaylightMixRepositoryTest {
    private fun song(id: String) = Song(id, "Track $id", "Artist", null)
    @After fun clearFixtures() { DaylightMixRepository.clear(); LikeState.clear() }

    @Test fun `large liked libraries cannot crowd out local music or grow the pool indefinitely`() {
        DaylightMixRepository.clear()
        DaylightMixRepository.prime((1..5_000).map { song("remote-$it") } + (1..500).map { song("local-$it").copy(localUri = "file:///music/$it.flac") })
        val pool = DaylightMixRepository.snapshot()
        assertEquals(400, pool.count { it.localUri == null })
        assertEquals(100, pool.count { it.localUri != null })
        DaylightMixRepository.prime(pool + pool)
        assertEquals(500, DaylightMixRepository.snapshot().size)
    }
    @Test fun `account scope changes discard the previous listeners sampled music`() {
        val old = Innertube.cookie
        try {
            DaylightMixRepository.prime(listOf(song("private-first")))
            Innertube.cookie = "SAPISID=daylight-mix-scope-fixture"
            assertTrue(DaylightMixRepository.snapshot().isEmpty())
            DaylightMixRepository.prime(listOf(song("private-second")))
            assertEquals(listOf("private-second"), DaylightMixRepository.snapshot().map { it.videoId })
        } finally { Innertube.cookie = old }
    }
    @Test fun `dislikes and removed favorites disappear without another network request`() {
        DaylightMixRepository.clear()
        DaylightMixRepository.prime(listOf(song("one"), song("two"), song("three")))
        LikeState.set("two", LikeStatus.DISLIKE)
        DaylightMixRepository.remove("one")
        assertEquals(listOf("three"), DaylightMixRepository.snapshot().map { it.videoId })
    }
}
