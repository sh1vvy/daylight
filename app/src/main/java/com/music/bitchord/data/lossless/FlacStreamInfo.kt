package com.music.bitchord.data.lossless

/** Measured from FLAC STREAMINFO, never inferred from a provider's quality label. */
data class FlacStreamInfo(
    val sampleRateHz: Int,
    val bitDepth: Int,
    val channels: Int,
    val totalSamples: Long,
) {
    val durationMs: Long get() = totalSamples * 1_000 / sampleRateHz
    val isHiRes: Boolean get() = bitDepth > 16 || sampleRateHz > 48_000

    companion object {
        const val HEADER_BYTES = 42

        fun read(header: ByteArray): FlacStreamInfo? {
            if (header.size < HEADER_BYTES ||
                !header.copyOfRange(0, 4).contentEquals(byteArrayOf(102, 76, 97, 67)) ||
                (header[4].toInt() and 0x7f) != 0 ||
                header[5] != 0.toByte() || header[6] != 0.toByte() || header[7] != 34.toByte()
            ) return null
            var packed = 0L
            for (i in 18..25) packed = (packed shl 8) or (header[i].toLong() and 0xff)
            val rate = ((packed ushr 44) and 0xfffff).toInt()
            val channels = ((packed ushr 41) and 7).toInt() + 1
            val depth = ((packed ushr 36) and 31).toInt() + 1
            val samples = packed and 0xfffffffffL
            // Unknown-length or implausible files cannot establish recording timing.
            if (rate !in 8_000..384_000 || depth !in 4..32 || samples == 0L) return null
            return FlacStreamInfo(rate, depth, channels, samples)
        }
    }
}
