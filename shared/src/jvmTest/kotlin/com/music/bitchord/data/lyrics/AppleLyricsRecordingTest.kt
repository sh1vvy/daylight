package com.music.bitchord.data.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonElement
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/** Metadata captured from the two Espresso recordings on 2026-10-09; no lyric text copied. */
class AppleLyricsRecordingTest {
    private val cleanIsrc = "USUM72404979"
    private val explicitIsrc = "USUM72403305"
    private val clean = hit(cleanIsrc)
    private val explicit = hit(explicitIsrc)
    private val hits = listOf(clean, explicit)

    private fun hit(isrc: String) = BiniLyrics.Hit(
        trackName = "Espresso",
        artistName = "Sabrina Carpenter",
        albumName = "Short n' Sweet",
        duration = 175,
        isrc = isrc,
        timingType = "word",
        lyricsUrl = "https://lrc.red/s/$isrc.ttml",
    )

    private fun catalogue(
        isrc: String = explicitIsrc,
        rating: String = "explicit",
        title: String = "Espresso",
        artist: String = "Sabrina Carpenter",
        album: String = "Short n' Sweet",
        duration: Long = 175_459,
        hasLyrics: Boolean = true,
    ): JsonElement = lyricsJson.parseToJsonElement(
        """{"results":{"songs":{"data":[{"id":"1750307247","attributes":{
            "name":"$title","artistName":"$artist","albumName":"$album",
            "durationInMillis":$duration,"isrc":"$isrc","contentRating":"$rating",
            "hasLyrics":$hasLyrics}}]}}}""",
    )

    private fun select(apple: JsonElement = catalogue(), choices: List<BiniLyrics.Hit> = hits) =
        AppleLyricsRecording.preferredExplicitHit(
            choices, apple, "Espresso", "Sabrina Carpenter", 175_000, "Short n' Sweet",
        )

    @Test
    fun verifiedExplicitRecordingWinsEvenWhenCleanCatalogueHitComesFirst() {
        assertEquals(explicit, select())
    }

    @Test
    fun providerSongIdUsesTheVerifiedIsrcAndLeavesUnknownRecordingsToNormalFallback() {
        val apple = lyricsJson.parseToJsonElement("""{"results":{"songs":{"data":[
            {"id":"clean-song-id","attributes":{"isrc":"$cleanIsrc"}},
            {"id":"explicit-song-id","attributes":{"isrc":"$explicitIsrc"}}
        ]}}}""")
        assertEquals("explicit-song-id", AppleLyricsRecording.songIdForRecording(apple, explicitIsrc))
        assertEquals("clean-song-id", AppleLyricsRecording.songIdForRecording(apple, cleanIsrc))
        assertNull(AppleLyricsRecording.songIdForRecording(apple, "USUM72405539"))
        assertNull(AppleLyricsRecording.songIdForRecording(apple, "../untrusted"))
    }

    @Test
    fun ratingWithoutMatchingRecordingOrLyricsIsInsufficient() {
        assertNull(select(catalogue(isrc = "USUM72405539")))
        assertNull(select(catalogue(rating = "clean")))
        assertNull(select(catalogue(rating = "")))
        assertNull(select(catalogue(hasLyrics = false)))
        assertNull(select(choices = listOf(clean)))
    }

    @Test
    fun titleArtistAlbumAndDurationMustAllAgreeInBothCatalogues() {
        assertNull(select(catalogue(title = "Espresso (Mochapella Version)")))
        assertNull(select(catalogue(artist = "Sabrina Carpenter Tribute")))
        assertNull(select(catalogue(album = "Espresso EP")))
        assertNull(select(catalogue(duration = 178_000)))
        for (mismatch in listOf(
            explicit.copy(trackName = "Espresso (Live)"),
            explicit.copy(artistName = "Another Artist"),
            explicit.copy(albumName = "Another Album"),
            explicit.copy(duration = 180),
            explicit.copy(duration = null),
            explicit.copy(isrc = null),
        )) assertNull(select(choices = listOf(clean, mismatch)))
    }

    @Test
    fun documentMustBeTimedAndItsPublishedUrlMustNameTheVerifiedIsrc() {
        for (mismatch in listOf(
            explicit.copy(timingType = "plain"),
            explicit.copy(timingType = null),
            explicit.copy(lyricsUrl = "https://lrc.red/s/$cleanIsrc.ttml"),
            explicit.copy(lyricsUrl = "https://unrelated.example/$explicitIsrc.ttml"),
            explicit.copy(lyricsUrl = "http://lrc.red/s/$explicitIsrc.ttml"),
            explicit.copy(lyricsUrl = "https://lrc.red/s/$explicitIsrc.ttml?another=recording"),
        )) assertNull(select(choices = listOf(mismatch)))
        val archivedDocument = explicit.copy(lyricsUrl = "https://lyrics-storage.binimum.org/$explicitIsrc.ttml")
        assertEquals(archivedDocument, select(choices = listOf(archivedDocument)))
        assertEquals(explicit.copy(timingType = "line"), select(choices = listOf(explicit.copy(timingType = "line"))))
    }

    @Test
    fun unknownPlayingAlbumCanUseOtherMatchingMetadataButUnknownDurationCannot() {
        assertEquals(explicit, AppleLyricsRecording.preferredExplicitHit(
            hits, catalogue(), "Espresso", "Sabrina Carpenter", 175_000, null,
        ))
        assertNull(AppleLyricsRecording.preferredExplicitHit(
            hits, catalogue(), "Espresso", "Sabrina Carpenter", 0, null,
        ))
    }

    @Test
    fun caseAndWhitespaceDoNotChangeTheRecordingButVersionWordsRemainSignificant() {
        assertEquals(explicit, select(catalogue(title = " espresso ", artist = "SABRINA  CARPENTER")))
        assertNull(select(catalogue(title = "Espresso (Clean)")))
    }

    @Test
    fun disabledRefinementAndIncompleteTrackMetadataMakeNoCatalogueRequest() = runBlocking {
        var called = false
        val lookup: suspend (String, String) -> JsonElement? = { _, _ -> called = true; catalogue() }
        val result = AppleLyricsRecording.identify(
            hits, "Espresso", "Sabrina Carpenter", 175_000, "Short n' Sweet", false, lookup,
        )!!
        assertEquals(clean, result.hit)
        assertFalse(result.verifiedExplicit)
        AppleLyricsRecording.identify(hits, "Espresso", "Sabrina Carpenter", 0, null, true, lookup)
        assertFalse(called)
    }

    @Test
    fun concurrentForegroundAndNotificationLookupsShareOneSuccessfulCatalogueRequest() = runBlocking {
        val calls = AtomicInteger()
        val payload = catalogue()
        val responses = List(2) {
            async {
                AppleLyricsRecording.cachedCatalogue("fixture simultaneous lookup", "artist") {
                    calls.incrementAndGet()
                    delay(50)
                    payload
                }
            }
        }.awaitAll()
        assertEquals(listOf(payload, payload), responses)
        assertEquals(1, calls.get())
    }

    @Test
    fun failedCatalogueJsonDoesNotPreventTheNextRequestFromRecovering() = runBlocking {
        val error = lyricsJson.parseToJsonElement("""{"error":"temporarily unavailable"}""")
        val payload = catalogue()
        assertEquals(error, AppleLyricsRecording.cachedCatalogue("fixture transient error", "artist") { error })
        assertEquals(payload, AppleLyricsRecording.cachedCatalogue("fixture transient error", "artist") { payload })
    }

    @Test
    fun missingAmbiguousAndFailedOptionalLookupRetainOriginalHit() = runBlocking {
        for (lookup in listOf<suspend (String, String) -> JsonElement?>(
            { _, _ -> null },
            { _, _ -> catalogue(rating = "clean") },
            { _, _ -> throw IOException("catalogue unavailable") },
        )) {
            val result = AppleLyricsRecording.identify(
                hits, "Espresso", "Sabrina Carpenter", 175_000, "Short n' Sweet", true, lookup,
            )!!
            assertEquals(clean, result.hit)
            assertFalse(result.verifiedExplicit)
        }
        assertNull(AppleLyricsRecording.identify(
            emptyList(), "Espresso", "Sabrina Carpenter", 175_000, null, true,
        ) { _, _ -> error("An empty catalogue must not be searched") })
    }

    @Test
    fun timedOutRefinementFallsBackAndOuterCancellationStillCancelsTheLookup() = runBlocking {
        val result = withTimeout(1_800) {
            AppleLyricsRecording.identify(
                hits, "Espresso", "Sabrina Carpenter", 175_000, "Short n' Sweet", true,
            ) { _, _ -> delay(5_000); catalogue() }
        }!!
        assertEquals(clean, result.hit)
        assertFalse(result.verifiedExplicit)
        assertFailsWith<CancellationException> {
            withTimeout(50) {
                AppleLyricsRecording.identify(
                    hits, "Espresso", "Sabrina Carpenter", 175_000, null, true,
                ) { _, _ -> delay(5_000); catalogue() }
            }
        }
        Unit
    }

    @Test
    fun optionalHttpLookupActuallyStopsWaitingWhenItsDeadlineExpires() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setHeadersDelay(3, TimeUnit.SECONDS).setBody("{}"))
            server.start()
            val lookup = async(start = CoroutineStart.UNDISPATCHED) {
                lyricsGetCatalogue(server.url("/catalogue").toString())
            }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            assertFalse(lookup.isCompleted, "The request should still be waiting for response headers")
            withTimeout(1_000) { lookup.cancelAndJoin() }
        } finally {
            server.shutdown()
        }
    }
}
