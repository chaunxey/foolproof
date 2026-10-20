package com.foolproof.features.duplicates.domain

import kotlin.math.abs

/**
 * 重复分组与默认预选规则（宪法 §3.2 / v1.10）。
 *
 * 纯函数：输入扫描结果，输出分组与默认勾选；不触碰平台 API，可直接单测。
 */
object DuplicateGrouper {

    /** 汉明距离阈值（宪法 §3.2 写死为 ≤5）。 */
    const val DEFAULT_THRESHOLD = 5

    /** 低信息量图片的补充条件：宽高比差与体积差的容忍度。 */
    private const val ASPECT_TOLERANCE = 0.02f
    private const val SIZE_TOLERANCE = 0.02

    /**
     * 流式贪心分组：每张图与已有组的**代表图**比较，命中则入组，否则新建组。
     *
     * 只返回成员数 ≥ 2 的组；单张图没有清理价值。
     */
    fun group(
        items: List<PhotoItem>,
        threshold: Int = DEFAULT_THRESHOLD,
    ): List<PhotoGroup> {
        val buckets = mutableListOf<Bucket>()
        for (item in items) {
            val bucket = buckets.firstOrNull { it.accepts(item, threshold) }
            if (bucket != null) {
                bucket.items += item
            } else {
                buckets += Bucket(item)
            }
        }
        return buckets
            .filter { it.items.size > 1 }
            .map { PhotoGroup(id = it.representative.hash.toULong().toString(), items = it.items) }
    }

    /**
     * 默认预选：每组勾选「除体积最大一张以外的其余项」。
     *
     * 返回待删除的 id 集合；**这不等于删除**，仍需用户确认（宪法 v1.10）。
     */
    fun defaultSelection(group: PhotoGroup): Set<String> {
        val keep = group.largest.id
        return group.items.map { it.id }.filter { it != keep }.toSet()
    }

    /** 批量版本，供扫描完成后初始化勾选状态。 */
    fun defaultSelection(groups: List<PhotoGroup>): Set<String> =
        groups.flatMap { defaultSelection(it) }.toSet()

    private class Bucket(val representative: PhotoItem) {
        val items = mutableListOf(representative)

        fun accepts(candidate: PhotoItem, threshold: Int): Boolean {
            if (PerceptualHash.hamming(representative.hash, candidate.hash) > threshold) return false

            // 任一张是低信息量（纯色/近乎纯色）时，仅靠距离不足以判定，
            // 必须同时满足宽高比接近与体积接近，否则宁可分到不同组。
            val lowInformationPair = representative.lowInformation || candidate.lowInformation
            if (!lowInformationPair) return true

            val aspectClose = abs(representative.aspectRatio - candidate.aspectRatio) <= ASPECT_TOLERANCE
            val sizeClose = relativeDifference(representative.sizeBytes, candidate.sizeBytes) <= SIZE_TOLERANCE
            return aspectClose && sizeClose
        }

        private fun relativeDifference(a: Long, b: Long): Double {
            val max = maxOf(a, b)
            if (max <= 0L) return 0.0
            return abs(a - b).toDouble() / max
        }
    }
}
