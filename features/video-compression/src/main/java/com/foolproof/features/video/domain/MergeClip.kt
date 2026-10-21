package com.foolproof.features.video.domain

/** 拼接列表里的一段：源视频 + 该段的起止点（v2 支持逐段裁剪）。 */
data class MergeClip(
    val source: SourceVideo,
    val startMs: Long = 0L,
    val endMs: Long = source.durationMs,
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    val isTrimmed: Boolean
        get() = startMs > 0L || endMs < source.durationMs
}
