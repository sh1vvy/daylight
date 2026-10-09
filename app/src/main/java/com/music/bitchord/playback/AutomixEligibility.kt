package com.music.bitchord.playback

import com.music.bitchord.data.NerdStats
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.settings.LosslessQuality
import com.music.bitchord.playback.smart.AutomixAudioPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Live activation gate; saved AutoMix preference survives a Hi-Res track or quality change. */
object AutomixEligibility {
    private val mutableAllowed = MutableStateFlow(true)
    val allowed: StateFlow<Boolean> = mutableAllowed

    fun hiResSelected(): Boolean = AppSettings.losslessQuality.value == LosslessQuality.HI_RES

    fun canActivateNow(): Boolean = AutomixAudioPolicy.mayEnable(hiResSelected(), NerdStats.current.value)

    fun analysisAllowed(): Boolean = AutomixAudioPolicy.mayAnalyze(hiResSelected(), NerdStats.current.value)

    internal fun publish(current: NerdStats.Snapshot?) {
        mutableAllowed.value = AutomixAudioPolicy.mayEnable(hiResSelected(), current)
    }

    internal fun reset() { mutableAllowed.value = !hiResSelected() }
}
