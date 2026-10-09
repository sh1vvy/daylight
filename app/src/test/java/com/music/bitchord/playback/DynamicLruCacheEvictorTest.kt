package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import java.io.File
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class DynamicLruCacheEvictorTest {
    /** In-memory Cache callbacks reproduce SimpleCache's synchronous span bookkeeping. */
    private class Fixture(limit: Long, headBytes: Long = 0, headBudget: Long = 0) {
        val evictor = DynamicLruCacheEvictor(limit, headBytes, headBudget)
        val spans = mutableListOf<CacheSpan>()
        val removed = mutableListOf<CacheSpan>()
        val cache = Proxy.newProxyInstance(Cache::class.java.classLoader, arrayOf(Cache::class.java)) { proxy, method, args ->
            when (method.name) {
                "removeSpan" -> {
                    val span = args!![0] as CacheSpan
                    check(spans.remove(span)) { "Evictor removed an unknown span: $span" }
                    removed += span
                    evictor.onSpanRemoved(proxy as Cache, span)
                    null
                }
                "getCacheSpace" -> bytes()
                "toString" -> "InMemoryCache"
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                else -> error("Unexpected cache call ${method.name}")
            }
        } as Cache

        fun add(key: String, bytes: Long, touched: Long, position: Long = 0): CacheSpan {
            val span = CacheSpan(key, position, bytes, touched, File("unused-$key-$position.cache"))
            spans += span
            evictor.onSpanAdded(cache, span)
            assertTrue("Hard ceiling exceeded after $key", bytes() <= evictor.maxBytes)
            return span
        }

        fun touch(span: CacheSpan, timestamp: Long): CacheSpan {
            assertTrue(spans.remove(span))
            val updated = CacheSpan(span.key, span.position, span.length, timestamp, span.file)
            spans += updated
            evictor.onSpanTouched(cache, span, updated)
            assertTrue(bytes() <= evictor.maxBytes)
            return updated
        }

        fun remove(span: CacheSpan) {
            assertTrue(spans.remove(span))
            evictor.onSpanRemoved(cache, span)
        }

        fun bytes() = spans.sumOf { it.length }
        fun keys() = spans.map { it.key }.toSet()
    }

    @Test fun oldestUnpreferredAudioIsEvictedBeforeAnOlderCurrentTrack() {
        val fixture = Fixture(100)
        fixture.evictor.preferredTracks = setOf("current")
        fixture.add("current", 40, 1)
        fixture.add("old", 40, 2)
        fixture.add("next", 40, 3)
        assertEquals(setOf("current", "next"), fixture.keys())
        assertEquals(listOf("old"), fixture.removed.map { it.key })
    }

    @Test fun everyRenditionOfPreviousCurrentAndUpcomingTracksSharesThePreference() {
        val fixture = Fixture(80)
        fixture.evictor.preferredTracks = setOf("previous-2", "previous-1", "current", "next-1", "next-2", "next-3", "next-4", "next-5")
        fixture.add("previous-2#lossless-a", 10, 1)
        fixture.add("previous-1#alt", 10, 2)
        fixture.add("current#lossless-b", 10, 3)
        for (index in 1..5) fixture.add("next-$index#lossless-$index", 10, (index + 3).toLong())
        fixture.add("unrelated", 20, 20)
        assertEquals(80, fixture.bytes())
        assertFalse("unrelated" in fixture.keys())
        assertEquals(listOf("unrelated"), fixture.removed.map { it.key })
    }

    @Test fun preferredTracksRemainBestEffortAndCannotExceedTheHardCeiling() {
        val fixture = Fixture(100)
        fixture.evictor.preferredTracks = setOf("previous-2", "previous-1", "current", "next")
        fixture.add("previous-2", 40, 1)
        fixture.add("previous-1", 40, 2)
        fixture.add("current", 40, 3)
        fixture.add("next", 40, 4)
        assertEquals(setOf("current", "next"), fixture.keys())
        assertEquals(listOf("previous-2", "previous-1"), fixture.removed.map { it.key })
        assertEquals(80, fixture.bytes())
    }

    @Test fun anUnpreferredProtectedHeadIsEvictedBeforePreferredAudio() {
        val fixture = Fixture(100, headBytes = 10, headBudget = 100)
        fixture.evictor.preferredTracks = setOf("current", "next")
        fixture.add("current", 40, 1, position = 100)
        fixture.add("old-head", 40, 2)
        fixture.add("next", 40, 3, position = 100)
        assertEquals(setOf("current", "next"), fixture.keys())
        assertEquals(listOf("old-head"), fixture.removed.map { it.key })
    }

    @Test fun protectedHeadsSurviveTailPressureWithinTheirBudget() {
        val fixture = Fixture(100, headBytes = 10, headBudget = 40)
        fixture.add("song", 40, 1)
        fixture.add("song", 40, 2, position = 100)
        fixture.add("other", 40, 3, position = 100)
        assertTrue(fixture.spans.any { it.key == "song" && it.position == 0L })
        assertEquals(listOf(100L), fixture.removed.map { it.position })
    }

    @Test fun preferredHeadsAreReleasedWhenOnlyTheyRemainAtTheByteCeiling() {
        val fixture = Fixture(100, headBytes = 100, headBudget = 1_000)
        fixture.evictor.preferredTracks = setOf("previous", "current", "next")
        fixture.add("previous", 40, 1)
        fixture.add("current", 40, 2)
        fixture.add("next", 40, 3)
        assertEquals(setOf("current", "next"), fixture.keys())
        assertEquals(listOf("previous"), fixture.removed.map { it.key })
        assertEquals(80, fixture.bytes())
    }

    @Test fun aSingleOversizedHeadCannotBreakTheCacheCeiling() {
        val fixture = Fixture(100, headBytes = 100, headBudget = 1_000)
        fixture.evictor.preferredTracks = setOf("current")
        fixture.add("current", 1_000, 1)
        assertEquals(0, fixture.bytes())
        assertEquals(1, fixture.removed.size)
    }

    @Test fun reducingTheBudgetReclaimsPreferredAndProtectedBytesImmediately() {
        val fixture = Fixture(200, headBytes = 100, headBudget = 1_000)
        fixture.evictor.preferredTracks = setOf("current", "next")
        fixture.add("old", 40, 1)
        fixture.add("current", 60, 2)
        fixture.add("next", 60, 3)
        fixture.evictor.maxBytes = 70
        fixture.evictor.applyNow(fixture.cache)
        assertEquals(setOf("next"), fixture.keys())
        assertEquals(60, fixture.bytes())
    }

    @Test fun changingThePlaybackWindowChangesEvictionWithoutReopeningTheCache() {
        val fixture = Fixture(100)
        fixture.evictor.preferredTracks = setOf("old-current")
        fixture.add("old-current", 40, 1)
        fixture.add("new-current", 40, 2)
        fixture.evictor.preferredTracks = setOf("new-current", "next")
        fixture.add("next", 40, 3)
        assertEquals(setOf("new-current", "next"), fixture.keys())
    }

    @Test fun touchesRenewLruAndDoNotDoubleCountBytes() {
        val fixture = Fixture(100)
        val first = fixture.add("first", 40, 1)
        fixture.add("second", 40, 2)
        fixture.touch(first, 4)
        fixture.add("third", 40, 3)
        assertEquals(setOf("first", "third"), fixture.keys())
        assertEquals(80, fixture.bytes())
    }

    @Test fun reservationEvictsBeforeWritingAndUnknownLengthsStayBoundedOnCommit() {
        val fixture = Fixture(100, headBytes = 100, headBudget = 1_000)
        fixture.add("old", 40, 1)
        fixture.add("current", 40, 2)
        fixture.evictor.onStartFile(fixture.cache, "next", 0, 50)
        assertTrue(fixture.bytes() + 50 <= fixture.evictor.maxBytes)
        fixture.evictor.onStartFile(fixture.cache, "unknown", 0, C.LENGTH_UNSET.toLong())
        fixture.add("unknown", 70, 3)
        assertTrue(fixture.bytes() <= fixture.evictor.maxBytes)
    }

    @Test fun equalTouchTimesAndRemovalCallbacksKeepByteAccountingCorrect() {
        val fixture = Fixture(120, headBytes = 10, headBudget = 80)
        val first = fixture.add("first", 40, 1)
        fixture.add("second", 40, 1)
        fixture.remove(first)
        fixture.add("third", 60, 1)
        assertEquals(100, fixture.bytes())
        fixture.evictor.maxBytes = 50
        fixture.evictor.applyNow(fixture.cache)
        assertTrue(fixture.bytes() <= 50)
    }
}
