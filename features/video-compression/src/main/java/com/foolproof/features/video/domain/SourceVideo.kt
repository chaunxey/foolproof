package com.foolproof.features.video.domain

/**
 * 用户选中的源视频（只读元信息，不含任何 Android 平台对象）。
 */
data class SourceVideo(
    /** Photo Picker 返回的 content uri 字符串。 */
    val id: String,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val frameRate: Int,
    val hasAudio: Boolean,
) {
    /** 短边像素：档位按短边定义（480p/720p/1080p 都是短边）。 */
    val shortSide: Int
        get() = minOf(width, height)

    val isLandscape: Boolean
        get() = width >= height
}
