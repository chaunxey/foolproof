package com.foolproof.features.duplicates.domain

import kotlin.math.abs

/**
 * 重复分组与默认预选规则（宪法 §3.2 / v1.10 / v1.14）。
 *
 * 纯函数：输入扫描结果，输出分组与默认勾选；不触碰平台 API，可直接单测。
 */
object DuplicateGrouper {

    /** 汉明距离阈值（宪法 §3.2 写死为 ≤5）。 */
    const val DEFAULT_THRESHOLD = 5

    /** dHash 距离放宽上限；超过 5 时必须通过灰度相关性校验。 */
    private const val EXTENDED_THRESHOLD = 12
    private const val CORRELATION_THRESHOLD = 0.92

    /** 低信息量图片的补充条件：宽高比差与体积差的容忍度。 */
    private const val ASPECT_TOLERANCE = 0.02f
    private const val SIZE_TOLERANCE = 0.02

    /**
     * 分组。两条规则组合，兼顾「不拆分同图」与「不链式合并」：
     *
     * 1. **完全相同哈希优先**：若已有组里存在哈希完全相同的成员，直接进那一组。
     *    这保证同一张图的副本（哪怕与组代表的距离略大）不会被分到两组。
     * 2. 否则与各组的**代表**比较，取距离最小且 ≤ 阈值的那一组；都不满足则新建组。
     *    只跟代表比较是为了避免"链式合并"：实测若与组内所有成员比较，
     *    26 张不同截图会串成一个 11 张的大组（相邻两张都相似，但整体并不相同）。
     *
     * 只返回成员数 ≥ 2 的组；单张图没有清理价值。
     */
    fun group(
        items: List<PhotoItem>,
        threshold: Int = DEFAULT_THRESHOLD,
    ): List<PhotoGroup> {
        val buckets = mutableListOf<Bucket>()
        for (item in items) {
            // 规则 1：完全相同的哈希优先并入（但仍要过纯色保护：
            // 纯白与纯黑的 dHash 都是 0，不能因为"哈希相同"就合并）
            val exactMatch = buckets.firstOrNull { bucket ->
                bucket.items.any { it.hash == item.hash && isAcceptable(it, item) }
            }
            if (exactMatch != null) {
                exactMatch.items += item
                continue
            }

            // 规则 2：按代表比较，取距离最小的组（仍用代表，避免链式合并）
            var best: Bucket? = null
            var bestDistance = Int.MAX_VALUE
            for (bucket in buckets) {
                val distance = bucket.acceptDistance(item, threshold) ?: continue
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = bucket
                }
            }
            if (best != null) {
                best.items += item
            } else {
                buckets += Bucket(item)
            }
        }
        return buckets
            .filter { it.items.size > 1 }
            .map { PhotoGroup(id = it.items.first().hash.toULong().toString(), items = it.items) }
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

    /**
     * 纯色/低方差图的保护：任一张是低信息量时，仅靠哈希距离不足以判定，
     * 必须同时满足宽高比接近与体积接近，否则宁可分到不同组。
     */
    private fun isAcceptable(a: PhotoItem, b: PhotoItem): Boolean {
        if (!a.lowInformation && !b.lowInformation) return true
        val aspectClose = abs(a.aspectRatio - b.aspectRatio) <= ASPECT_TOLERANCE
        val sizeClose = relativeDifference(a.sizeBytes, b.sizeBytes) <= SIZE_TOLERANCE
        return aspectClose && sizeClose
    }

    private fun relativeDifference(a: Long, b: Long): Double {
        val max = maxOf(a, b)
        if (max <= 0L) return 0.0
        return abs(a - b).toDouble() / max
    }

    private class Bucket(val representative: PhotoItem) {
        val items = mutableListOf(representative)

        /**
         * 与**代表**比较，返回可接受的距离；不可接受时返回 null。
         *
         * 只比代表是有意为之：与所有成员比较会导致链式合并（见 [group] 的说明）。
         */
        fun acceptDistance(candidate: PhotoItem, threshold: Int): Int? {
            val distance = PerceptualHash.hamming(representative.hash, candidate.hash)
            if (distance > maxOf(threshold, EXTENDED_THRESHOLD)) return null
            if (!isAcceptable(representative, candidate)) return null
            if (distance <= threshold) return distance
            val similarity = PerceptualHash.correlation(representative.luma, candidate.luma)
            return if (similarity >= CORRELATION_THRESHOLD) distance else null
        }
    }
}
