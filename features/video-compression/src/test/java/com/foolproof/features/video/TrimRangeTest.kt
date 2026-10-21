package com.foolproof.features.video

import com.foolproof.features.video.domain.TrimRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrimRangeTest {

    @Test
    fun `full range covers whole video`() {
        val range = TrimRange.full(20_000)

        assertEquals(0L, range.startMs)
        assertEquals(20_000L, range.endMs)
        assertEquals(20_000L, range.durationMs)
        assertTrue(range.isFullRange(20_000))
    }

    @Test
    fun `valid range keeps values`() {
        val range = TrimRange.create(3_000, 8_000, 20_000)

        assertEquals(3_000L, range.startMs)
        assertEquals(8_000L, range.endMs)
        assertEquals(5_000L, range.durationMs)
        assertFalse(range.isFullRange(20_000))
    }

    @Test
    fun `end is pushed to minimum duration`() {
        // 起点 10s，终点只给 10.2s → 必须夹到 11s
        val range = TrimRange.create(10_000, 10_200, 20_000)

        assertEquals(10_000L, range.startMs)
        assertEquals(11_000L, range.endMs)
        assertEquals(TrimRange.MIN_DURATION_MS, range.durationMs)
    }

    @Test
    fun `start is clamped so that minimum segment fits`() {
        // 源 20s，起点给 19.8s → 最多只能到 19s
        val range = TrimRange.create(19_800, 20_000, 20_000)

        assertEquals(19_000L, range.startMs)
        assertEquals(20_000L, range.endMs)
    }

    @Test
    fun `negative start and overflow end are clamped`() {
        val range = TrimRange.create(-5_000, 99_000, 20_000)

        assertEquals(0L, range.startMs)
        assertEquals(20_000L, range.endMs)
    }

    @Test
    fun `video shorter than minimum degrades to full range`() {
        val range = TrimRange.create(200, 900, 800)

        assertEquals(0L, range.startMs)
        assertEquals(800L, range.endMs)
    }

    @Test
    fun `zero duration source yields empty range`() {
        val range = TrimRange.create(0, 0, 0)

        assertEquals(0L, range.durationMs)
    }

    @Test
    fun `estimate follows trimmed duration`() {
        val preset = com.foolproof.features.video.domain.CompressionPreset()
        val range = TrimRange.create(3_000, 8_000, 20_000)

        assertEquals(
            preset.estimatedBytes(5_000),
            preset.estimatedBytes(range.durationMs),
        )
    }
}
