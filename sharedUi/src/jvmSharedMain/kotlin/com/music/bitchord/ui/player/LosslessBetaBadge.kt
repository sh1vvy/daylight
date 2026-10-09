package com.music.bitchord.ui.player

import com.music.bitchord.data.NerdStats

internal enum class ConfirmedLosslessLabel { LOSSLESS, HI_RES }

/** Normal playback remains unlabelled; a provider's claimed format cannot light the badge. */
internal fun confirmedLosslessLabel(stats: NerdStats.Snapshot?): ConfirmedLosslessLabel? = when {
    stats?.isLossless != true -> null
    stats.isHiRes -> ConfirmedLosslessLabel.HI_RES
    else -> ConfirmedLosslessLabel.LOSSLESS
}
