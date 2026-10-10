package com.music.bitchord.data

import com.music.bitchord.data.model.*
import kotlin.test.*
import org.junit.Test

class RecommendationLanguagesTest {
    @Test fun pickerCoversAllLanguagesAndCanonicalizesRegionalTags() {
        assertTrue(RecommendationLanguages.catalog.size > 150)
        assertEquals(setOf("es", "hi"), RecommendationLanguages.canonical(setOf("es-MX", "hi-IN", "fake")))
    }
    @Test fun generalMultiLanguageFilteringPreservesHistoryAndUnknownTracks() {
        val track = ShelfItem("Saved Hindi song", "", null, "saved", null)
        val feed = listOf(HomeShelf("Recents", listOf(track)), HomeShelf("Hindi hits", listOf(track)),
            HomeShelf("Español", listOf(track)), HomeShelf("For you", listOf(ShelfItem("Daylight", "Taylor Swift", null, "new", null))))
        assertEquals(listOf("Recents", "For you"), RecommendationLanguages.filter(feed, setOf("hi", "es")).map { it.title })
        assertEquals(feed, RecommendationLanguages.filter(feed, emptySet()))
    }
    @Test fun languageWordsMustBeWholeLabelsAndNationalityIsNotInferred() {
        assertTrue(RecommendationLanguages.allows(Song("a", "Spanish Harlem", "A", null), setOf("hi")))
        assertTrue(RecommendationLanguages.allows(Song("b", "Unknown", "Hindi artist", null), setOf("hi")))
        assertTrue(RecommendationLanguages.allows(Song("c", "माया", "A", null), setOf("hi")))
        assertFalse(RecommendationLanguages.allows(Song("d", "Song (हिंदी)", "A", null), setOf("hi")))
    }
    @Test fun songAndArtistNamesAreNotTreatedAsLanguageLabels() {
        assertTrue(RecommendationLanguages.allows(ShelfItem("Spanish Harlem", "French Montana", null, "unlabelled", null), setOf("es", "fr")))
        assertFalse(RecommendationLanguages.allows(Song("labelled", "Song (French version)", "A", null), setOf("fr")))
    }
    @Test fun knownShelfLanguageAlsoFiltersTheSameSongInAnotherRecommendationRow() {
        val song = ShelfItem("Unlabelled", "Artist", null, "metadata-hint", null)
        val shelves = listOf(HomeShelf("Spanish songs", listOf(song)), HomeShelf("For you", listOf(song)))
        assertTrue(RecommendationLanguages.filter(shelves, setOf("es")).isEmpty())
        assertTrue(RecommendationLanguages.allows(song, emptySet()))
    }
    @Test fun languageHintsStayWithinTheirMemoryBudgetAndRejectOldAccountResults() {
        repeat(1_025) { RecommendationLanguageHints.record("bounded-$it", "es-MX") }
        assertNull(RecommendationLanguageHints.language("bounded-0"))
        assertEquals("es", RecommendationLanguageHints.language("bounded-1024"))
        RecommendationLanguageHints.record("outdated", "es", Long.MIN_VALUE)
        assertNull(RecommendationLanguageHints.language("outdated"))
    }
}
