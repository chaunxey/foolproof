package com.foolproof.features.duplicates

import com.foolproof.features.duplicates.domain.DuplicateGrouper
import com.foolproof.features.duplicates.domain.PerceptualHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHashTest {

    private fun gray9x8(fill: Int) = IntArray(PerceptualHash.WIDTH * PerceptualHash.HEIGHT) { fill }

    @Test
    fun `hamming distance of identical hashes is zero`() {
        assertEquals(0, PerceptualHash.hamming(0L, 0L))
        assertEquals(0, PerceptualHash.hamming(0b1011L, 0b1011L))
    }

    @Test
    fun `hamming distance counts differing bits`() {
        assertEquals(1, PerceptualHash.hamming(0L, 1L))
        // 全 0 与全 1（64 位）距离为 64
        assertEquals(64, PerceptualHash.hamming(0L, -1L))
    }

    @Test
    fun `solid color produces zero hash`() {
        val hash = PerceptualHash.dHash(gray9x8(128))

        assertEquals(0L, hash)
    }

    @Test
    fun `strictly increasing row sets all bits`() {
        // 每行从 0 递增到 8：左 < 右，都不置位；反过来左侧更大则置位
        val descending = IntArray(PerceptualHash.WIDTH * PerceptualHash.HEIGHT) { index ->
            val column = index % PerceptualHash.WIDTH
            200 - column * 10
        }
        val ascending = IntArray(descending.size) { index ->
            val column = index % PerceptualHash.WIDTH
            column * 10
        }

        assertEquals(-1L, PerceptualHash.dHash(descending))
        assertEquals(0L, PerceptualHash.dHash(ascending))
    }

    @Test
    fun `variance of solid color is zero and flagged low information`() {
        val gray = gray9x8(200)

        assertEquals(0.0, PerceptualHash.variance(gray), 0.0001)
        assertTrue(PerceptualHash.isLowInformation(gray))
    }

    @Test
    fun `normal photo is not low information`() {
        val gray = IntArray(PerceptualHash.WIDTH * PerceptualHash.HEIGHT) { index ->
            if (index % 2 == 0) 20 else 230
        }

        assertFalse(PerceptualHash.isLowInformation(gray))
    }

    @Test
    fun `grayscale conversion keeps luminance ordering`() {
        val gray = PerceptualHash.grayscale(intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()))

        assertEquals(0, gray[0])
        assertEquals(255, gray[1])
    }

    @Test
    fun `dHash rejects wrong input size`() {
        val thrown = runCatching { PerceptualHash.dHash(IntArray(10)) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    /**
     * 记录一条**实测结论**（推翻了原先的假设）：dHash 比较的是水平相邻像素，
     * 水平镜像会反转每一行的比较方向，使哈希近似「按位取反」，距离接近 64。
     *
     * 含义：镜像副本**不会**被误判为重复（这是好事），但也不会被识别出来（可接受的漏判）。
     */
    @Test
    fun `mirrored content produces a far distance`() {
        val original = IntArray(PerceptualHash.WIDTH * PerceptualHash.HEIGHT) { index ->
            val column = index % PerceptualHash.WIDTH
            30 + column * 25
        }
        val mirrored = IntArray(original.size) { index ->
            val row = index / PerceptualHash.WIDTH
            val column = index % PerceptualHash.WIDTH
            original[row * PerceptualHash.WIDTH + (PerceptualHash.WIDTH - 1 - column)]
        }

        val distance = PerceptualHash.hamming(
            PerceptualHash.dHash(original),
            PerceptualHash.dHash(mirrored),
        )

        assertTrue("镜像后的距离应很大（不会被误判为重复），实际 $distance", distance >= 32)
        assertFalse("镜像副本不应落入阈值为 5 的同一组", distance <= DuplicateGrouper.DEFAULT_THRESHOLD)
    }
}
