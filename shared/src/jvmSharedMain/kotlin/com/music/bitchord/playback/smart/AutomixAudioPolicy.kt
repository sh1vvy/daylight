package com.music.bitchord.playback.smart

import com.music.bitchord.data.NerdStats

/** AutoMix works with lossy audio and standard lossless, never with Hi-Res samples. */
object AutomixAudioPolicy {
    enum class Verdict { ALLOWED, WAITING_FOR_FORMAT, HI_RES }

    fun verdict(format: NerdStats.Snapshot?): Verdict = when {
        format?.mimeType.isNullOrBlank() -> Verdict.WAITING_FOR_FORMAT
        format!!.isHiRes -> Verdict.HI_RES
        // A FLAC claim without its actual resolution cannot prove it is safe to mix.
        format.isLossless && ((format.bitDepth ?: 0) <= 0 || (format.sampleRateHz ?: 0) <= 0) -> Verdict.WAITING_FOR_FORMAT
        else -> Verdict.ALLOWED
    }

    /** A preference can be enabled before a track loads; the audible decks still need proof. */
    fun mayEnable(hiResSelected: Boolean, current: NerdStats.Snapshot?): Boolean =
        !hiResSelected && verdict(current) != Verdict.HI_RES

    /** A queued format may be unknown, but known Hi-Res never spends a DSP/ML pass. */
    fun mayAnalyze(hiResSelected: Boolean, current: NerdStats.Snapshot?, next: NerdStats.Snapshot? = null): Boolean =
        !hiResSelected && verdict(current) != Verdict.HI_RES && verdict(next) != Verdict.HI_RES

    fun transitionVerdict(hiResSelected: Boolean, current: NerdStats.Snapshot?, next: NerdStats.Snapshot?): Verdict {
        val outgoing = verdict(current)
        val incoming = verdict(next)
        return when {
            hiResSelected || outgoing == Verdict.HI_RES || incoming == Verdict.HI_RES -> Verdict.HI_RES
            outgoing == Verdict.ALLOWED && incoming == Verdict.ALLOWED -> Verdict.ALLOWED
            else -> Verdict.WAITING_FOR_FORMAT
        }
    }
}

/**
 * Remembers a rejected queue pair after its silent standby decoder has been retired.
 * Without this, the missing format would cause the same Hi-Res item to be prepared
 * again every watcher tick. A different rendition/queue pair gets a fresh decision.
 */
class AutomixTransitionGate {
    private var rejectedPair: String? = null

    fun verdict(pair: String, hiResSelected: Boolean, current: NerdStats.Snapshot?, next: NerdStats.Snapshot?): AutomixAudioPolicy.Verdict {
        val actual = AutomixAudioPolicy.transitionVerdict(false, current, next)
        if (actual == AutomixAudioPolicy.Verdict.HI_RES) rejectedPair = pair
        // A real decoder fallback/replacement can make this same URI pair safe again.
        if (actual == AutomixAudioPolicy.Verdict.ALLOWED && rejectedPair == pair) rejectedPair = null
        return if (hiResSelected || rejectedPair == pair) AutomixAudioPolicy.Verdict.HI_RES else actual
    }

    fun reset() { rejectedPair = null }
}
