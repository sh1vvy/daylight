package com.music.bitchord.playback

import com.music.bitchord.data.model.QueueTier

/** Context/radio tracks are implicit; a deliberately selected or queued song can still be heard. */
internal fun dislikedQueueIndices(ids: List<String>, currentIndex: Int, tierAt: (Int) -> QueueTier, disliked: (String) -> Boolean): List<Int> =
    ids.indices.filter { it != currentIndex && tierAt(it) != QueueTier.USER_QUEUE && disliked(ids[it]) }
