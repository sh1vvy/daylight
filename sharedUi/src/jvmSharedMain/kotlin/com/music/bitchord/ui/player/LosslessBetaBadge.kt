/* DAYLIGHT-ATTRIBUTION
 * Daylight modifications by sh1vvy.
 * Copyright (C) 2026 sh1vvy, for copyrightable Daylight contributions only.
 * Daylight changes: 2026-10-09 through 2026-10-09.
 * Daylight additions: GNU GPLv3 with attribution/origin terms in DAYLIGHT-NOTICE.md.
 * Original authors' notices and licenses remain applicable to their material.
 */

package com.music.bitchord.ui.player

import com.music.bitchord.data.NerdStats

internal enum class ConfirmedLosslessLabel { LOSSLESS, HI_RES }

/** Normal playback remains unlabelled; a provider's claimed format cannot light the badge. */
internal fun confirmedLosslessLabel(stats: NerdStats.Snapshot?): ConfirmedLosslessLabel? = when {
    stats?.isLossless != true -> null
    stats.isHiRes -> ConfirmedLosslessLabel.HI_RES
    else -> ConfirmedLosslessLabel.LOSSLESS
}
