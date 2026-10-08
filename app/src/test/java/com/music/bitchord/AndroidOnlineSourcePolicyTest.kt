package com.music.bitchord

import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.AudioQuality
import com.music.bitchord.data.sources.SourceConfig
import com.music.bitchord.data.sources.SourceKind
import com.music.bitchord.data.sources.SourceRegistry
import com.music.bitchord.data.sources.SourceResolver
import com.music.bitchord.data.sources.StreamRequest
import com.music.bitchord.data.sources.TrackMatcher
import com.music.bitchord.playback.QualityUpgrade
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOnlineSourcePolicyTest {
    @Test
    fun `old enabled providers and priorities cannot replace YouTube Music`() {
        val stored = legacySources()
        val active = SourceRegistry.enabledConfigs(stored)

        assertEquals(listOf(SourceKind.YOUTUBE), active.map { it.kind })
        assertEquals("youtube", active.single().id)
        assertTrue(active.single().enabled)
        assertTrue(active.single().allowDownloads)
        assertFalse(active.single().checkValidLossless)
        // The runtime policy must not remove the user's saved source or token.
        assertEquals("https://private.example/?token=kept", stored.first().baseUrl)
        assertFalse(stored.last().enabled)
        assertFalse(stored.last().allowDownloads)
    }

    @Test
    fun `restored lists missing YouTube still have a built in catalogue`() {
        val active = SourceRegistry.enabledConfigs(legacySources().dropLast(1))
        assertEquals(listOf(SourceKind.YOUTUBE), active.map { it.kind })
        assertTrue(active.single().enabled)
        assertTrue(active.single().isComplete)
    }

    @Test
    fun `only YouTube participates in runtime playback and download walks`() {
        val saved = SourceRegistry.configs.value
        try {
            SourceRegistry.configs.value = legacySources()
            assertEquals(listOf(SourceKind.YOUTUBE), SourceRegistry.activeForPlayback().map { it.kind })
            assertEquals(listOf(SourceKind.YOUTUBE), SourceRegistry.activeForDownload().map { it.kind })
            assertFalse(SourceResolver.canSubstituteForYouTube())
        } finally {
            SourceRegistry.configs.value = saved
        }
    }

    @Test
    fun `legacy quality preference cannot start provider prefetch or upgrade work`() = runBlocking {
        val saved = SourceRegistry.configs.value
        try {
            SourceRegistry.configs.value = legacySources()
            val target = TrackMatcher.Target(title = "A song", artist = "A singer", durationSec = 180)
            assertNull(SourceResolver.substituteForYouTube(target))
            assertNull(SourceResolver.prefetchSubstitute(target))
            assertNull(SourceResolver.upgradeFor(target))
            assertNull(SourceResolver.forDownload(target, StreamRequest.Lossless))
            assertFalse(QualityUpgrade.settledForLess("youtube-item", target))
        } finally {
            SourceRegistry.configs.value = saved
        }
    }

    @Test
    fun `saved lossless ceiling requests best YouTube quality without overriding a mobile cap`() {
        val wifi = AppSettings.audioQualityWifi.value
        val cellular = AppSettings.audioQualityCellular.value
        val metered = AppSettings.meteredConnection.value
        try {
            AppSettings.audioQualityWifi.value = AudioQuality.LOSSLESS
            AppSettings.audioQualityCellular.value = AudioQuality.LOW
            AppSettings.meteredConnection.value = false
            assertEquals(StreamRequest.Best, SourceResolver.requestForNow())
            AppSettings.meteredConnection.value = true
            assertEquals(StreamRequest.Capped(AudioQuality.LOW.maxKbps), SourceResolver.requestForNow())
        } finally {
            AppSettings.audioQualityWifi.value = wifi
            AppSettings.audioQualityCellular.value = cellular
            AppSettings.meteredConnection.value = metered
        }
    }

    private fun legacySources() = listOf(
        SourceConfig(id = "addon", kind = SourceKind.ADDON, baseUrl = "https://private.example/?token=kept"),
        SourceConfig(id = "module", kind = SourceKind.CUSTOM_MODULE, baseUrl = "https://legacy.example/index.json"),
        SourceConfig(id = "jio", kind = SourceKind.JIOSAAVN, enabled = true),
        SourceConfig(id = "youtube", kind = SourceKind.YOUTUBE, enabled = false, allowDownloads = false, checkValidLossless = true),
    )
}
