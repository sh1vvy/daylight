/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-11 through 2026-10-11.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.sh1vvy.daylight.library

import kotlinx.serialization.Serializable

/** Persistent catalogue metadata. Authentication, transport and UI state never belong here. */
@Serializable
data class LibraryOverview(
    val sections: List<LibrarySection>,
    /** Time of the last successful provider refresh, not the last local edit. */
    val verifiedAt: Long,
)

/** Empty successful sections remove old cards; failed sections retain their last usable data. */
fun mergeLibrarySections(previous: List<LibrarySection>, incoming: List<LibrarySection>, verifiedLabels: Set<String>): List<LibrarySection> {
    val retained = previous.filter { it.label !in verifiedLabels }
    return incoming + retained.filter { old -> incoming.none { it.label == old.label } }
}

@Serializable
data class LibrarySection(
    val label: String,
    val cards: List<LibraryCard>,
    val caption: String = "",
    val destination: String? = null,
    val destinationParameters: String? = null,
)

@Serializable
data class LibraryCard(
    val label: String,
    val byline: String,
    val artwork: String?,
    val collectionId: String?,
    val recordingId: String?,
)
