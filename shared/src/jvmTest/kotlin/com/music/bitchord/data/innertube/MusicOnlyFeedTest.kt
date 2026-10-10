package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class MusicOnlyFeedTest {
    @Test
    fun homeDropsShowAndEpisodeShelvesEvenWhenTheirCardsHaveNoType() {
        val root = home(
            carousel("Your shows", card("VLshow", "A show")),
            carousel("New episodes", track("episode", "A title")),
            carousel("Listen again", track("song", "Daylight")),
        )
        val shelves = InnertubeParser.parseHome(root)
        assertEquals(listOf("Listen again"), shelves.map { it.title })
        assertEquals(listOf("song"), shelves.single().items.map { it.videoId })
    }

    @Test
    fun homeContinuationsAlsoDropPodcastShelvesAndEmptyLocalizedShowShelves() {
        val root = json("""{"continuationContents":{"contents":[
            ${carousel("New episodes", track("episode", "New episode"))},
            ${carousel("Vos émissions", card("show", "Une émission", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"))},
            ${carousel("Albums", card("MPREbalbum", "An album", "MUSIC_PAGE_TYPE_ALBUM"))}
        ]}}""")
        assertEquals(listOf("Albums"), InnertubeParser.parseHomeContinuation(root).map { it.title })
    }

    @Test
    fun mixedRecommendationsKeepSongsAndUncataloguedMusicButDropTypedPodcastsInAnyLanguage() {
        val root = home(carousel("Pour vous",
            card("show", "Une émission", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE"),
            track("episode", "Un épisode", type = "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"),
            card("MPEDuncatalogued", "A music upload"),
            track("song", "Episode"),
        ))
        assertEquals(listOf("uncatalogued", "song"), InnertubeParser.parseHome(root).single().items.map { it.videoId })
    }

    @Test
    fun libraryGridAndListExcludeShowsAndTheNewEpisodesAutoPlaylist() {
        val root = json("""{"items":[
            ${card("show", "A show", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE")},
            ${card("VLRDPN", "Nouveaux épisodes")},
            ${card("VLSE", "Gespeicherte Folgen")},
            ${card("MPSPPLshow", "Unclassified show")},
            ${card("VLplaylist", "New episodes", "MUSIC_PAGE_TYPE_PLAYLIST")},
            ${browseRow("show-list", "Another show", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE")},
            ${browseRow("MPREbalbum", "An album", "MUSIC_PAGE_TYPE_ALBUM")}
        ]}""")
        assertEquals(listOf("VLplaylist", "MPREbalbum"), InnertubeParser.parseLibraryItems(root).map { it.browseId })
    }

    @Test
    fun searchFiltersPromotedShowsEpisodesAndOlderSubtitleOnlyPodcastRows() {
        val root = json("""{"items":[
            {"musicCardShelfRenderer":{
                "title":{"runs":[{"text":"A promoted show"}]},
                "onTap":${browseEndpoint("show", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE")}
            }},
            ${browseRow("show-list", "A show", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE")},
            ${row("episode", "An episode", "Episode • Host • 42:00")},
            ${row("podcast", "A podcast", "Podcast • Host")},
            ${row("song", "Podcast", "Song • Artist • 3:00")}
        ]}""")
        assertEquals(listOf("song"), InnertubeParser.parseSearchSongs(root).map { it.videoId })
    }

    @Test
    fun videosSearchDropsPodcastEpisodesButKeepsMusicVideos() {
        val root = json("""{"items":[
            ${row("episode", "Un épisode", "Video • Host • 40:00", "MUSIC_VIDEO_TYPE_PODCAST_EPISODE")},
            ${row("video", "A music video", "Video • Artist • 3:00", "MUSIC_VIDEO_TYPE_OMV")}
        ]}""")
        assertEquals(1, InnertubeParser.parseSearchPage(root, includeVideos = true).rows.size)
        assertEquals("video", InnertubeParser.parseSearchPage(root, includeVideos = true).rows.single().let {
            (it as com.music.bitchord.data.model.SearchResult.Track).song.videoId
        })
    }

    @Test
    fun playlistAndRadioRecommendationsDropTypedEpisodes() {
        val playlist = json("""{"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[
            ${row("episode", "An episode", "Host", "MUSIC_VIDEO_TYPE_PODCAST_EPISODE")},
            ${row("song", "New Episodes", "Artist", "MUSIC_VIDEO_TYPE_ATV")}
        ]}}}""")
        assertEquals(listOf("song"), InnertubeParser.parsePlaylistShelf(playlist)?.songs?.map { it.videoId })
        val queue = json("""{"items":[
            {"playlistPanelVideoRenderer":{"videoId":"episode","title":{"runs":[{"text":"An episode"}]},
                "navigationEndpoint":${watchEndpoint("episode", "MUSIC_VIDEO_TYPE_PODCAST_EPISODE")}}},
            {"playlistPanelVideoRenderer":{"videoId":"song","title":{"runs":[{"text":"Daylight"}]}}}
        ]}""")
        assertEquals(listOf("song"), InnertubeParser.parseWatchQueue(queue).map { it.videoId })
    }

    @Test
    fun aPodcastLinkInASongsMenuDoesNotHideTheSong() {
        val root = json("""{"musicResponsiveListItemRenderer":{
            "playlistItemData":{"videoId":"song"},
            "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Daylight"}]}}}],
            "menu":{"navigationEndpoint":${browseEndpoint("show", "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE")}}
        }}""")
        assertEquals(listOf("song"), InnertubeParser.parseSearchSongs(root).map { it.videoId })
    }

    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject
    private fun home(vararg shelves: String) = json("""{"contents":{"singleColumnBrowseResultsRenderer":{"tabs":[{
        "tabRenderer":{"content":{"sectionListRenderer":{"contents":[${shelves.joinToString()}]}}}
    }]}}}""")
    private fun carousel(title: String, vararg items: String) = """{"musicCarouselShelfRenderer":{
        "header":{"musicCarouselShelfBasicHeaderRenderer":{"title":{"runs":[{"text":"$title"}]}}},
        "contents":[${items.joinToString()}]
    }}"""
    private fun browseEndpoint(id: String, type: String) = """{"browseEndpoint":{"browseId":"$id",
        "browseEndpointContextSupportedConfigs":{"browseEndpointContextMusicConfig":{"pageType":"$type"}}
    }}"""
    private fun watchEndpoint(id: String, type: String) = """{"watchEndpoint":{"videoId":"$id",
        "watchEndpointMusicSupportedConfigs":{"watchEndpointMusicConfig":{"musicVideoType":"$type"}}
    }}"""
    private fun card(id: String, title: String, type: String = "") = """{"musicTwoRowItemRenderer":{
        "title":{"runs":[{"text":"$title"}]},"navigationEndpoint":${browseEndpoint(id, type)}
    }}"""
    private fun track(id: String, title: String, type: String = "MUSIC_VIDEO_TYPE_ATV") = """{"musicTwoRowItemRenderer":{
        "title":{"runs":[{"text":"$title"}]},"navigationEndpoint":${watchEndpoint(id, type)}
    }}"""
    private fun row(id: String, title: String, subtitle: String, type: String = "") = """{"musicResponsiveListItemRenderer":{
        "playlistItemData":{"videoId":"$id"},"navigationEndpoint":${watchEndpoint(id, type)},
        "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$subtitle"}]}}}
        ]
    }}"""
    private fun browseRow(id: String, title: String, type: String) = """{"musicResponsiveListItemRenderer":{
        "navigationEndpoint":${browseEndpoint(id, type)},
        "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}}]
    }}"""
}
