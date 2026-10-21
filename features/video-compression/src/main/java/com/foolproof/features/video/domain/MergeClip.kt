package com.foolproof.features.video.domain

/** 拼接列表里的一段：源视频 + 该段的起止点（v2 支持逐段裁剪）。 */
data class MergeClip(
    val source: SourceVideo,
    val startMs: Long = 0L,
    val endMs: Long = source.durationMs,
    /** 加入顺序的稳定序号，用于分配不重复的行底色（拖拽后不变）。 */
    val colorSeed: Int = 0,
) {
    val durationMs: Long
        get() = (endMs - startMs).coerceAtLeast(0L)

    val isTrimmed: Boolean
        get() = startMs > 0L || endMs < source.durationMs
}
