package com.foolproof.features.video.domain

import kotlinx.coroutines.flow.Flow

/** 多段视频按顺序首尾拼接（支持每段裁剪）。 */
interface VideoMerger {

    fun merge(
        clips: List<MergeClip>,
        outputPath: String,
        targetShortSide: Int,
        frameRate: Int,
        bitrateKbps: Int,
    ): Flow<VideoProgress>

    suspend fun cancel()

    /** 取消/失败后删除半成品（Transformer 停止是异步的）。 */
    suspend fun cleanupPartialFile(path: String)
}
