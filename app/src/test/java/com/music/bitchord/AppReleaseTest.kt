package com.music.bitchord

import com.music.bitchord.data.AppRelease
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppReleaseTest {
    private val devId = "com.sh1vvy.daylight.dev"
    private val prodId = "com.sh1vvy.daylight"
    private val version = "0.2.0"

    private fun asset(name: String, state: String = "uploaded", url: String = "https://github.com/sh1vvy/daylight/releases/download/v$version/$name") =
        buildJsonObject {
            put("name", name)
            put("state", state)
            put("browser_download_url", url)
        }

    private fun release(vararg assets: JsonObject) = buildJsonObject {
        put("assets", JsonArray(assets.toList()))
    }

    @Test
    fun `dev updates select their own package even when production is uploaded first`() {
        val dev = asset("daylight-dev.apk")
        val prod = asset("daylight.apk")
        assertEquals(dev["browser_download_url"]?.let { (it as JsonPrimitive).content },
            AppRelease.apkAssetUrl(release(prod, dev), devId, version))
    }

    @Test
    fun `production updates select their own package with the legacy dev asset first`() {
        val dev = asset("daylight-dev.apk")
        val prod = asset("daylight.apk")
        assertEquals(prod["browser_download_url"]?.let { (it as JsonPrimitive).content },
            AppRelease.apkAssetUrl(release(dev, prod), prodId, version))
    }

    @Test
    fun `missing compatible package opens the release page instead of downloading the other app`() {
        assertNull(AppRelease.apkAssetUrl(release(asset("daylight.apk")), devId, version))
        assertNull(AppRelease.apkAssetUrl(release(asset("daylight-dev.apk")), prodId, version))
        assertNull(AppRelease.apkAssetUrl(release(asset("daylight.apk")), "$prodId.benchmark", version))
    }

    @Test
    fun `stable universal names take priority over versioned and architecture specific assets`() {
        val page = release(
            asset("daylight-0.2.0-arm64-v8a.apk"),
            asset("daylight-0.2.0.apk"),
            asset("daylight-0.2.0-dev.apk"),
            asset("daylight.apk"),
            asset("daylight-dev.apk"),
        )
        assertTrue(AppRelease.apkAssetUrl(page, prodId, version)!!.endsWith("/daylight.apk"))
        assertTrue(AppRelease.apkAssetUrl(page, devId, version)!!.endsWith("/daylight-dev.apk"))
    }

    @Test
    fun `matching versioned universal assets remain supported`() {
        val page = release(asset("daylight-0.2.0-dev.apk"), asset("daylight-0.2.0.apk"))
        assertTrue(AppRelease.apkAssetUrl(page, devId, version)!!.endsWith("/daylight-0.2.0-dev.apk"))
        assertTrue(AppRelease.apkAssetUrl(page, prodId, version)!!.endsWith("/daylight-0.2.0.apk"))
        assertNull(AppRelease.apkAssetUrl(page, prodId, "0.2.1"))
    }

    @Test
    fun `unfinished empty or malformed assets cannot become an install download`() {
        val page = release(
            asset("daylight.apk", state = "new"),
            asset("daylight.apk", url = ""),
            buildJsonObject { put("name", JsonObject(emptyMap())) },
        )
        assertNull(AppRelease.apkAssetUrl(page, prodId, version))
        assertNull(AppRelease.apkAssetUrl(JsonObject(emptyMap()), prodId, version))
        assertNull(AppRelease.apkAssetUrl(buildJsonObject { put("assets", "unexpected") }, prodId, version))
    }

    @Test
    fun `initial release updates installed 0 1 9 users and never prompts on the same release`() {
        assertTrue(AppRelease.isNewer("0.2.0", "0.1.9"))
        assertFalse(AppRelease.isNewer("0.2.0", "0.2.0"))
        assertFalse(AppRelease.isNewer("0.2.0", "0.2.1"))
    }

    @Test
    fun `numeric versions compare by component rather than alphabetically`() {
        assertTrue(AppRelease.isNewer("0.10.0", "0.9.9"))
        assertFalse(AppRelease.isNewer("0.9.9", "0.10.0"))
        assertFalse(AppRelease.isNewer("0.2", "0.2.0"))
        assertTrue(AppRelease.isNewer("0.2.0.1", "0.2.0"))
    }

    @Test
    fun `final release also updates a beta of the same version`() {
        assertTrue(AppRelease.isNewer("0.2.0", "0.2.0-beta1"))
        assertFalse(AppRelease.isNewer("0.2.0-beta1", "0.2.0"))
        assertFalse(AppRelease.isNewer("0.2.0-beta1", "0.2.0-beta1"))
    }

    @Test
    fun `development sequence advances numerically and stable outranks its prereleases`() {
        assertTrue(AppRelease.isNewer("0.2.2-dev.2", "0.2.2-dev.1"))
        assertTrue(AppRelease.isNewer("0.2.2-dev.10", "0.2.2-dev.9"))
        assertFalse(AppRelease.isNewer("0.2.2-dev.2", "0.2.2-dev.10"))
        assertFalse(AppRelease.isNewer("0.2.2-dev.1", "0.2.2-dev.1"))
        assertTrue(AppRelease.isNewer("0.2.2", "0.2.2-dev.10"))
        assertFalse(AppRelease.isNewer("0.2.2-dev.10", "0.2.2"))
        assertTrue(AppRelease.isNewer("0.2.2-dev.1", "0.2.1"))
        assertFalse(AppRelease.isNewer("not-a-version", "0.2.1"))
    }

    private fun channelRelease(tag: String, dev: Boolean = false, prerelease: Boolean = false, draft: Boolean = false) =
        buildJsonObject {
            put("tag_name", "v$tag")
            put("html_url", "https://github.com/sh1vvy/daylight/releases/tag/v$tag")
            put("prerelease", prerelease)
            put("draft", draft)
            put("assets", JsonArray(listOf(asset(if (dev) "daylight-dev.apk" else "daylight.apk"))))
        }

    @Test
    fun `stable never offers a development prerelease even with a production asset`() {
        val dev = channelRelease("0.2.2-dev.1", prerelease = true)
        val stable = channelRelease("0.2.1")
        assertNull(AppRelease.selectUpdate(JsonArray(listOf(dev, stable)), prodId, "0.2.1"))
        assertEquals(stable, AppRelease.selectUpdate(JsonArray(listOf(dev, stable)), prodId, "0.2.0"))
        assertTrue(AppRelease.releasesUrl(prodId).endsWith("/latest"))
        assertTrue(AppRelease.releasesUrl(devId).contains("?per_page="))
    }

    @Test
    fun `dev selects the newest compatible release independent of API ordering`() {
        val one = channelRelease("0.2.2-dev.1", dev = true, prerelease = true)
        val ten = channelRelease("0.2.2-dev.10", dev = true, prerelease = true)
        val two = channelRelease("0.2.2-dev.2", dev = true, prerelease = true)
        assertEquals(ten, AppRelease.selectUpdate(JsonArray(listOf(two, ten, one)), devId, "0.2.1"))
        assertNull(AppRelease.selectUpdate(JsonArray(listOf(one, two, ten)), devId, "0.2.2-dev.10"))
    }

    @Test
    fun `drafts other packages unrelated betas and malformed releases cannot update Dev`() {
        val invalid = JsonArray(listOf(
            channelRelease("0.2.2-dev.2", dev = true, prerelease = true, draft = true),
            channelRelease("0.2.2-dev.3", prerelease = true),
            channelRelease("0.9.0-beta1", dev = true, prerelease = true),
            channelRelease("0.9.0-dev.1", dev = true),
            channelRelease("oops", dev = true, prerelease = true),
            JsonPrimitive("unexpected"),
        ))
        assertNull(AppRelease.selectUpdate(invalid, devId, "0.2.2-dev.1"))
        assertNull(AppRelease.selectUpdate(invalid, "$devId.benchmark", "0.2.1"))
    }

    @Test
    fun `Dev can move from a prerelease to a newer stable release of its own package`() {
        val stable = channelRelease("0.2.2", dev = true)
        val dev = channelRelease("0.2.2-dev.10", dev = true, prerelease = true)
        assertEquals(stable, AppRelease.selectUpdate(JsonArray(listOf(dev, stable)), devId, "0.2.2-dev.9"))
        assertNull(AppRelease.selectUpdate(stable, devId, "0.2.2"))
    }
}
