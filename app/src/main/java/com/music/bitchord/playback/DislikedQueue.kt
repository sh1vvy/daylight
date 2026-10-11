/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-10 through 2026-10-10.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier

/** Context/radio tracks are implicit; a deliberately selected or queued song can still be heard. */
internal fun dislikedQueueIndices(ids: List<String>, currentIndex: Int, tierAt: (Int) -> QueueTier, disliked: (String) -> Boolean): List<Int> =
    ids.indices.filter { it != currentIndex && tierAt(it) != QueueTier.USER_QUEUE && disliked(ids[it]) }
