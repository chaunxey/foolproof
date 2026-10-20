package com.foolproof.features.compression.domain

/**
 * 待压缩的原图。
 *
 * 注意：**不持有 `android.net.Uri`**，用字符串 [id] 表示图片。这样 ViewModel 与纯逻辑可以在
 * JVM 单测里运行，不需要 Robolectric；Uri 与 id 的互转只发生在 UI/数据层边界。
 */
data class SourceImage(
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val width: Int,
    val height: Int,
) {
    val isHeic: Boolean
        get() = mimeType?.lowercase() in HEIC_MIME_TYPES

    companion object {
        val HEIC_MIME_TYPES = setOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence")
    }
}
