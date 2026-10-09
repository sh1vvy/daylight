package com.music.bitchord.data.lossless

import org.junit.Assert.*
import org.junit.Test

class FlacStreamInfoTest {
    @Test fun `CD header yields actual resolution channels and timing`() {
        val info = FlacStreamInfo.read(flacHeader())!!
        assertEquals(44_100, info.sampleRateHz)
        assertEquals(16, info.bitDepth)
        assertEquals(2, info.channels)
        assertEquals(210_000L, info.durationMs)
        assertFalse(info.isHiRes)
    }

    @Test fun `regular lossless permits 24 bit through 48k while higher rates are hi res`() {
        assertFalse(FlacStreamInfo.read(flacHeader(44_100, 24))!!.isHiRes)
        assertFalse(FlacStreamInfo.read(flacHeader(48_000, 24))!!.isHiRes)
        assertTrue(FlacStreamInfo.read(flacHeader(96_000, 16))!!.isHiRes)
        assertFalse(FlacStreamInfo.read(flacHeader(48_000, 16))!!.isHiRes)
    }

    @Test fun `short wrong block length unknown duration and impossible rate reject`() {
        val wrongBlock = flacHeader().also { it[4] = 1 }
        val wrongLength = flacHeader().also { it[7] = 33 }
        for (bytes in listOf(ByteArray(0), flacHeader().copyOf(41), ByteArray(42), wrongBlock, wrongLength, flacHeader(rate = 0), flacHeader(seconds = 0))) {
            assertNull(FlacStreamInfo.read(bytes))
        }
    }
}
