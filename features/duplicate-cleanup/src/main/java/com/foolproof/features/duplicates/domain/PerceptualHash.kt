package com.foolproof.features.duplicates.domain

/**
 * 感知哈希的纯函数实现（宪法 §3.2：dHash + 汉明距离）。
 *
 * 全部为纯 Kotlin，不触碰 Android API，可直接 JVM 单测。
 *
 * 已知识别局限（v1 接受，文档中注明）：
 * - **镜像翻转不敏感**：水平翻转后 dHash 变化很小，可能被判为重复；
 * - **纯色图**：所有相邻像素相等 → 哈希为 0，必须靠 [isLowInformation] 额外保护。
 */
object PerceptualHash {

    /** dHash 的采样网格：每行 9 个像素产生 8 个比较位。 */
    const val WIDTH = 9
    const val HEIGHT = 8
    const val BIT_COUNT = 64

    /**
     * 灰度方差阈值。低于此值视为「低信息量」（纯色/近乎纯色）。
     * 取 16 ≈ 标准差 4（0–255 灰度），足以识别纯色与渐变极小的截图。
     */
    const val LOW_INFORMATION_VARIANCE = 16.0

    /** ARGB 像素 → 灰度（Rec.601 整数近似）。 */
    fun grayscale(pixels: IntArray): IntArray {
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            gray[i] = (r * 299 + g * 587 + b * 114) / 1000
        }
        return gray
    }

    /**
     * 对 9×8 的灰度数组计算 dHash：每行比较相邻像素，左大于右记 1。
     *
     * @throws IllegalArgumentException 输入尺寸不是 [WIDTH]×[HEIGHT]
     */
    fun dHash(gray: IntArray): Long {
        require(gray.size == WIDTH * HEIGHT) {
            "dHash 需要 ${WIDTH * HEIGHT} 个像素，实际 ${gray.size}"
        }
        var hash = 0L
        var bit = 0
        for (y in 0 until HEIGHT) {
            val row = y * WIDTH
            for (x in 0 until WIDTH - 1) {
                if (gray[row + x] > gray[row + x + 1]) {
                    hash = hash or (1L shl bit)
                }
                bit++
            }
        }
        return hash
    }

    /** 汉明距离：不同位的个数。 */
    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** 灰度方差。 */
    fun variance(gray: IntArray): Double {
        if (gray.isEmpty()) return 0.0
        var sum = 0.0
        for (value in gray) sum += value
        val mean = sum / gray.size
        var acc = 0.0
        for (value in gray) {
            val diff = value - mean
            acc += diff * diff
        }
        return acc / gray.size
    }

    /** 纯色 / 近乎纯色：这些图的哈希没有区分度，分组时必须额外约束。 */
    fun isLowInformation(gray: IntArray): Boolean = variance(gray) < LOW_INFORMATION_VARIANCE
}
