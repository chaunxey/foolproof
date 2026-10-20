package com.foolproof.features.compression.platform

/**
 * 大图降采样计算。纯函数，不触碰 Android API，可直接单测。
 *
 * `inSampleSize` 必须是 2 的幂，这是 `BitmapFactory` 的要求。
 */
object BitmapSampling {

    const val DEFAULT_MAX_DIMENSION = 8192
    const val DEFAULT_MAX_PIXELS = 40_000_000L

    /** 是否需要降采样。 */
    fun requiresDownscale(
        width: Int,
        height: Int,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        maxPixels: Long = DEFAULT_MAX_PIXELS,
    ): Boolean = calculateInSampleSize(width, height, maxDimension, maxPixels) > 1

    /**
     * 计算满足「长边 ≤ [maxDimension] 且总像素 ≤ [maxPixels]」的最小 2 的幂。
     * 尺寸非法（≤0）时返回 1，交给上层按解码失败处理。
     */
    fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int = DEFAULT_MAX_DIMENSION,
        maxPixels: Long = DEFAULT_MAX_PIXELS,
    ): Int {
        if (width <= 0 || height <= 0) return 1
        if (maxDimension <= 0) return 1

        var sample = 1
        while (true) {
            val sampledWidth = width / sample
            val sampledHeight = height / sample
            val withinDimension = sampledWidth <= maxDimension && sampledHeight <= maxDimension
            val withinPixels = sampledWidth.toLong() * sampledHeight.toLong() <= maxPixels
            if (withinDimension && withinPixels) return sample

            // 防止极端输入下 int 溢出：sample 已是 2 的幂，超过 2^15 后再乘会溢出。
            if (sample >= MAX_SAMPLE) return MAX_SAMPLE
            sample *= 2
        }
    }

    private const val MAX_SAMPLE = 1 shl 15
}
