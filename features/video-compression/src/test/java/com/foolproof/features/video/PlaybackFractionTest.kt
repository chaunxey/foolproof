package com.foolproof.features.video

import com.foolproof.features.video.ui.playbackFraction
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFractionTest {

    @Test
    fun `start of the trim range is zero`() {
        assertEquals(0f, playbackFraction(positionMs = 5_000, startMs = 5_000, endMs = 15_000), 1e-4f)
    }

    @Test
    fun `middle of the trim range is half`() {
        assertEquals(0.5f, playbackFraction(positionMs = 10_000, startMs = 5_000, endMs = 15_000), 1e-4f)
    }

    @Test
    fun `end of the trim range is one`() {
        assertEquals(1f, playbackFraction(positionMs = 15_000, startMs = 5_000, endMs = 15_000), 1e-4f)
    }

    @Test
    fun `position before the start is clamped to zero`() {
        assertEquals(0f, playbackFraction(positionMs = 0, startMs = 5_000, endMs = 15_000), 1e-4f)
    }

    @Test
    fun `position past the end is clamped to one`() {
        assertEquals(1f, playbackFraction(positionMs = 99_000, startMs = 5_000, endMs = 15_000), 1e-4f)
    }

    @Test
    fun `degenerate range does not divide by zero`() {
        assertEquals(0f, playbackFraction(positionMs = 1_000, startMs = 1_000, endMs = 1_000), 1e-4f)
    }
}
