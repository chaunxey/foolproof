package com.foolproof.features.video.domain

/**
 * 裁剪区间（毫秒）。纯数据 + 纯校验，可直接 JVM 单测。
 *
 * 约束（spec FR-009）：最小区段 [MIN_DURATION_MS]，且不得超出源时长。
 */
data class TrimRange(
    val startMs: Long,
    val endMs: Long,
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0)

    /** 是否等价于「不裁剪」。 */
    fun isFullRange(sourceDurationMs: Long): Boolean =
        startMs <= 0L && endMs >= sourceDurationMs

    companion object {
        const val MIN_DURATION_MS = 1_000L

        /** 整段（不裁剪）。 */
        fun full(durationMs: Long): TrimRange = TrimRange(0L, durationMs.coerceAtLeast(0L))

        /**
         * 构造并**夹紧**一个合法区间：
         * - 源时长不足最小区段时，退化为整段；
         * - 起点不得为负，终点不得超过源时长；
         * - 终点至少比起点大 [MIN_DURATION_MS]。
         */
        fun create(startMs: Long, endMs: Long, sourceDurationMs: Long): TrimRange {
            if (sourceDurationMs <= 0L) return TrimRange(0L, 0L)
            if (sourceDurationMs <= MIN_DURATION_MS) return full(sourceDurationMs)

            val maxStart = sourceDurationMs - MIN_DURATION_MS
            val start = startMs.coerceIn(0L, maxStart)
            val end = endMs.coerceIn(start + MIN_DURATION_MS, sourceDurationMs)
            return TrimRange(start, end)
        }
    }
}
