package com.music.bitchord.ui.player

import com.music.bitchord.data.NerdStats

/** A fallback label needs an actual lossy decoder, not merely a failed lookup. */
internal fun betaFallbackIsVisible(status: NerdStats.LosslessBetaStatus?, stats: NerdStats.Snapshot?): Boolean =
    status?.isFallback == true && stats?.mimeType != null && !stats.isLossless && stats.sourceName == "YouTube"
