package com.foolproof.features.compression

import com.foolproof.features.compression.platform.BitmapSampling
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitmapSamplingTest {

    @Test
    fun `normal photo needs no downscale`() {
        assertEquals(1, BitmapSampling.calculateInSampleSize(4000, 3000))
        assertFalse(BitmapSampling.requiresDownscale(4000, 3000))
    }

    @Test
    fun `oversized dimension downscales by power of two`() {
        // 20000 / 2 = 10000 仍超 8192，再除 2 = 5000 满足
        assertEquals(4, BitmapSampling.calculateInSampleSize(20000, 10000))
        assertTrue(BitmapSampling.requiresDownscale(20000, 10000))
    }

    @Test
    fun `pixel budget triggers downscale even under dimension limit`() {
        // 8000 x 6000 = 48MP > 40MP，长边未超限，仍需降采样
        assertEquals(2, BitmapSampling.calculateInSampleSize(8000, 6000))
    }

    @Test
    fun `extreme aspect ratio is bounded by long side`() {
        // 20000 x 100：长边超限，sample=4 时长边 5000
        assertEquals(4, BitmapSampling.calculateInSampleSize(20000, 100))
    }

    @Test
    fun `invalid dimensions return one`() {
        assertEquals(1, BitmapSampling.calculateInSampleSize(0, 0))
        assertEquals(1, BitmapSampling.calculateInSampleSize(-10, 100))
    }

    @Test
    fun `huge panorama stays within int range`() {
        val sample = BitmapSampling.calculateInSampleSize(100_000, 50_000)

        assertTrue(sample >= 16)
        assertTrue(100_000 / sample <= BitmapSampling.DEFAULT_MAX_DIMENSION)
    }
}
