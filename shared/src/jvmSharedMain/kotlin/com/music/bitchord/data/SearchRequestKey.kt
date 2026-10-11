/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-10.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.data

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.model.SearchFilter
import java.util.Locale

/** Response identity without storing cookies, account names or other credentials. */
data class SearchRequestKey(
    val scope: Long,
    val language: String,
    val query: String,
    val filter: SearchFilter,
) {
    fun isCurrent(): Boolean = scope == Innertube.responseCacheScope && language == Innertube.currentLanguage

    companion object {
        private val spaces = Regex("\\s+")

        fun current(query: String, filter: SearchFilter = SearchFilter.ALL): SearchRequestKey = create(
            Innertube.responseCacheScope, Innertube.currentLanguage, query, filter,
        )

        fun create(scope: Long, language: String, query: String, filter: SearchFilter): SearchRequestKey =
            SearchRequestKey(scope, language, query.trim().replace(spaces, " ").lowercase(Locale.ROOT), filter)
    }
}
