package com.music.bitchord.data

import kotlinx.serialization.json.JsonObject
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

    private data class ParsedVersion(val parts: List<Int>, val isPreRelease: Boolean)

    private fun parseVersion(raw: String): ParsedVersion {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        return ParsedVersion(base.split(".").map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /** Numeric dotted comparison; the final release also outranks its beta. */
    fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.isPreRelease && !l.isPreRelease
    }
}
