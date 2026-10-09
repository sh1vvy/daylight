package com.music.bitchord.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.music.bitchord.data.NerdStats

/** The same decoder/FLAC STREAMINFO measurements used by Stats for nerds, for both decks. */
@UnstableApi
internal fun Format.automixSnapshot(): NerdStats.Snapshot {
    val info = automixFlacInfo()
    val dsd = codecs?.takeIf { it.startsWith("dsd") }
    val depth = when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
        // Float is a transport format, not the source's sample precision.
        else -> null
    }
    return NerdStats.Snapshot(
        mimeType = dsd?.let { "audio/$it" } ?: sampleMimeType,
        bitrateKbps = null,
        sampleRateHz = dsd?.removePrefix("dsd")?.toIntOrNull()?.times(44_100)
            ?: sampleRate.takeIf { it > 0 } ?: info?.first,
        channels = channelCount.takeIf { it > 0 },
        bitDepth = if (dsd != null) 1 else depth ?: info?.second,
    )
}

@UnstableApi
private fun Format.automixFlacInfo(): Pair<Int, Int>? {
    if (sampleMimeType != MimeTypes.AUDIO_FLAC) return null
    val data = initializationData.firstOrNull() ?: return null
    fun at(i: Int) = data[i].toInt() and 0xff
    fun headerAt(i: Int) = data.size >= i + 4 && at(i) and 0x7f == 0 && at(i + 1) == 0 && at(i + 2) == 0 && at(i + 3) == 34
    val body = when {
        data.size >= 4 && data[0] == 102.toByte() && data[1] == 76.toByte() && data[2] == 97.toByte() && data[3] == 67.toByte() -> if (headerAt(4)) 8 else 4
        headerAt(0) -> 4
        else -> 0
    }
    if (data.size < body + 34) return null
    val rate = (at(body + 10) shl 12) or (at(body + 11) shl 4) or (at(body + 12) shr 4)
    val depth = (((at(body + 12) and 1) shl 4) or (at(body + 13) shr 4)) + 1
    return (rate to depth).takeIf { rate > 0 }
}
