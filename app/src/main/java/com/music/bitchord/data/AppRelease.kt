package com.music.bitchord.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Release rules shared by the update check and its JVM tests. */
internal object AppRelease {
    /**
     * Development and production installs have different Android package IDs
     * and signing keys. Selecting the first APK would install the other app
     * beside this one instead of updating it, so each package has its own
     * universal asset. Stable names also give the Jam website a permanent URL.
     */
    fun apkAssetUrl(release: JsonObject, applicationId: String, version: String): String? {
        val names = when (applicationId) {
            "com.sh1vvy.daylight.dev" -> listOf("daylight-dev.apk", "daylight-$version-dev.apk")
            "com.sh1vvy.daylight" -> listOf("daylight.apk", "daylight-$version.apk")
            // A benchmark build cannot be replaced by either release package.
            else -> return null
        }
        val assets = runCatching {
            release["assets"]?.jsonArray?.mapNotNull { it as? JsonObject }
        }.getOrNull().orEmpty()
        for (name in names) {
            for (asset in assets) {
                val url = runCatching {
                    if (asset["name"]?.jsonPrimitive?.contentOrNull?.equals(name, ignoreCase = true) == true &&
                        asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
                    ) {
                        asset["browser_download_url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                }.getOrNull()
                if (url != null) return url
            }
        }
        return null
    }

    fun releasesUrl(applicationId: String): String =
        if (applicationId == "com.sh1vvy.daylight.dev") {
            "https://api.github.com/repos/sh1vvy/daylight/releases?per_page=30"
        } else {
            "https://api.github.com/repos/sh1vvy/daylight/releases/latest"
        }

    /** Stable installs never consume a prerelease; Dev can consume newer Dev or stable APKs. */
    fun selectUpdate(body: JsonElement, applicationId: String, currentVersion: String): JsonObject? {
        val development = applicationId == "com.sh1vvy.daylight.dev"
        if (!development && applicationId != "com.sh1vvy.daylight") return null
        val releases = when (body) {
            is JsonArray -> body.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(body)
            else -> emptyList()
        }
        var selected: JsonObject? = null
        var selectedVersion = currentVersion
        for (release in releases) {
            if (runCatching { release["draft"]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true) continue
            val version = runCatching { release["tag_name"]?.jsonPrimitive?.contentOrNull }
                .getOrNull()?.removePrefix("v") ?: continue
            val parsed = parseVersion(version) ?: continue
            val prerelease = runCatching { release["prerelease"]?.jsonPrimitive?.booleanOrNull }.getOrNull() == true
            if (prerelease || parsed.suffix.isNotEmpty()) {
                if (!development || !prerelease || !Regex("dev\\.[1-9][0-9]*").matches(parsed.suffix.joinToString("."))) continue
            }
            if (apkAssetUrl(release, applicationId, version) == null || !isNewer(version, selectedVersion)) continue
            selected = release
            selectedVersion = version
        }
        return selected
    }

    private data class ParsedVersion(val parts: List<Int>, val suffix: List<String>)

    private fun parseVersion(raw: String): ParsedVersion? {
        val match = Regex("([0-9]+(?:\\.[0-9]+)*)(?:-([A-Za-z0-9]+(?:\\.[A-Za-z0-9]+)*))?")
            .matchEntire(raw) ?: return null
        val parts = match.groupValues[1].split(".").map { it.toIntOrNull() ?: return null }
        return ParsedVersion(parts, match.groupValues[2].takeIf { it.isNotEmpty() }?.split(".").orEmpty())
    }

    /** Numeric dotted comparison; the final release also outranks its beta. */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest) ?: return false
        val c = parseVersion(current) ?: return false
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        if (l.suffix.isEmpty() || c.suffix.isEmpty()) return c.suffix.isNotEmpty() && l.suffix.isEmpty()
        for (i in 0 until minOf(l.suffix.size, c.suffix.size)) {
            val a = l.suffix[i]
            val b = c.suffix[i]
            if (a == b) continue
            val numericA = a.toLongOrNull()
            val numericB = b.toLongOrNull()
            return when {
                numericA != null && numericB != null -> numericA > numericB
                numericA != null -> false
                numericB != null -> true
                else -> a > b
            }
        }
        return l.suffix.size > c.suffix.size
    }
}
