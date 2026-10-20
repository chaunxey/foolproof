package com.foolproof.features.duplicates.domain

/**
 * 一组被判为重复的照片。
 *
 * [reclaimableBytes] 是「除最大一张外全部删掉」能释放的空间，与默认预选规则一致。
 */
data class PhotoGroup(
    /** 组标识（代表哈希的字符串形式），用作 Compose 列表 key。 */
    val id: String,
    val items: List<PhotoItem>,
) {
    val count: Int
        get() = items.size

    val totalBytes: Long
        get() = items.sumOf { it.sizeBytes }

    /** 体积最大的一张；并列时取先出现的（扫描顺序稳定）。 */
    val largest: PhotoItem
        get() = items.maxByOrNull { it.sizeBytes } ?: items.first()

    /** 预选删除后可释放的字节数。 */
    val reclaimableBytes: Long
        get() = (totalBytes - largest.sizeBytes).coerceAtLeast(0L)
}
