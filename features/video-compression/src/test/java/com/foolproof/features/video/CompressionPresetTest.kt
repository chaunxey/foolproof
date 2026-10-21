package com.foolproof.features.video

import com.foolproof.features.video.domain.BitrateTier
import com.foolproof.features.video.domain.CompressionPreset
import com.foolproof.features.video.domain.ResolutionTier
import com.foolproof.features.video.domain.SourceVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompressionPresetTest {

    private fun video(
        width: Int = 1920,
        height: Int = 1080,
        durationMs: Long = 30_000,
        hasAudio: Boolean = true,
    ) = SourceVideo(
        id = "content://video/1",
        displayName = "v.mp4",
        sizeBytes = 50_000_000,
        durationMs = durationMs,
        width = width,
        height = height,
        frameRate = 30,
        hasAudio = hasAudio,
    )

    @Test
    fun `bitrate table matches spec`() {
        assertEquals(8_000, CompressionPreset(ResolutionTier.P1080, BitrateTier.HIGH).videoBitrateKbps())
        assertEquals(5_000, CompressionPreset(ResolutionTier.P1080, BitrateTier.MEDIUM).videoBitrateKbps())
        assertEquals(3_000, CompressionPreset(ResolutionTier.P1080, BitrateTier.LOW).videoBitrateKbps())

        assertEquals(4_000, CompressionPreset(ResolutionTier.P720, BitrateTier.HIGH).videoBitrateKbps())
        assertEquals(2_500, CompressionPreset(ResolutionTier.P720, BitrateTier.MEDIUM).videoBitrateKbps())
        assertEquals(1_500, CompressionPreset(ResolutionTier.P720, BitrateTier.LOW).videoBitrateKbps())

        assertEquals(2_000, CompressionPreset(ResolutionTier.P480, BitrateTier.HIGH).videoBitrateKbps())
        assertEquals(1_200, CompressionPreset(ResolutionTier.P480, BitrateTier.MEDIUM).videoBitrateKbps())
        assertEquals(700, CompressionPreset(ResolutionTier.P480, BitrateTier.LOW).videoBitrateKbps())
    }

    @Test
    fun `target short side never upscales`() {
        // 源是 480p，选 1080p 档 → 仍是 480（不放大）
        val preset = CompressionPreset(ResolutionTier.P1080, BitrateTier.HIGH)
        val small = video(width = 854, height = 480)

        assertEquals(480, preset.targetShortSide(small))
    }

    @Test
    fun `target short side follows tier when source is larger`() {
        val preset = CompressionPreset(ResolutionTier.P720, BitrateTier.MEDIUM)
        val big = video(width = 3840, height = 2160)

        assertEquals(720, preset.targetShortSide(big))
    }

    @Test
    fun `target short side respects encoder limit and stays even`() {
        val preset = CompressionPreset(ResolutionTier.P1080, BitrateTier.HIGH)
        val big = video(width = 3840, height = 2160)

        // 模拟器软编上限 2048（>1080）；这里用一个更小的上限验证夹紧与偶数对齐
        assertEquals(1000, preset.targetShortSide(big, encoderMaxSize = 1001))
        assertEquals(1080, preset.targetShortSide(big, encoderMaxSize = 1080))
    }

    @Test
    fun `portrait video uses short side too`() {
        val portrait = video(width = 1080, height = 1920)
        val preset = CompressionPreset(ResolutionTier.P720, BitrateTier.MEDIUM)

        assertEquals(720, preset.targetShortSide(portrait))
        assertEquals(true, !portrait.isLandscape)
    }

    @Test
    fun `estimate uses video plus audio bitrate`() {
        val preset = CompressionPreset(ResolutionTier.P720, BitrateTier.MEDIUM) // 2500 + 128
        val estimate = preset.estimatedBytes(durationMs = 10_000, hasAudio = true)

        // (2500 + 128) kbps × 10s / 8 = 3285 KB ≈ 3.28 MB
        assertEquals(3_285_000L, estimate)
    }

    @Test
    fun `estimate drops audio when source has no audio track`() {
        val preset = CompressionPreset(ResolutionTier.P480, BitrateTier.LOW) // 700
        val estimate = preset.estimatedBytes(durationMs = 10_000, hasAudio = false)

        // 700 kbps × 10s / 8 = 875 KB
        assertEquals(875_000L, estimate)
    }

    @Test
    fun `estimate scales with trimmed duration`() {
        val preset = CompressionPreset(ResolutionTier.P720, BitrateTier.MEDIUM)
        val full = preset.estimatedBytes(durationMs = 20_000)
        val half = preset.estimatedBytes(durationMs = 10_000)

        assertEquals(full / 2, half)
    }

    @Test
    fun `estimate is zero for empty duration`() {
        assertEquals(0L, CompressionPreset().estimatedBytes(durationMs = 0))
    }

    @Test
    fun `summary shows resolution and bitrate`() {
        val text = CompressionPreset(ResolutionTier.P720, BitrateTier.MEDIUM).summary()

        assertTrue(text.contains("720p"))
        assertTrue(text.contains("2.5"))
    }

    @Test
    fun `lower tier always smaller than higher tier`() {
        val low = CompressionPreset(ResolutionTier.P480, BitrateTier.LOW).estimatedBytes(60_000)
        val high = CompressionPreset(ResolutionTier.P1080, BitrateTier.HIGH).estimatedBytes(60_000)

        assertTrue("低档必须明显小于高档", low < high / 2)
    }
}
