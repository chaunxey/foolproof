package com.foolproof.features.duplicates.domain

/**
 * 相册中的一张照片（扫描阶段产出）。
 *
 * [hash] 为 8×8 dHash 的 64 位结果；[lowInformation] 标记纯色/近乎纯色的图——
 * 这类图的哈希几乎全 0，**不能只靠汉明距离判定重复**（见 [PerceptualHash.isLowInformation]）。
 */
data class PhotoItem(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val hash: Long,
    val lowInformation: Boolean = false,
) {
    /** 宽高比；高度非法时返回 0，调用方据此走保守分支。 */
    val aspectRatio: Float
        get() = if (height <= 0) 0f else width.toFloat() / height
}
