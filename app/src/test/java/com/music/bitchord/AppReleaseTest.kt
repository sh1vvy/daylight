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
}
