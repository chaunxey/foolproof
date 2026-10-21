package com.foolproof.features.video.domain

/** 纯时长裁剪：只保留 [startMs]–[endMs]，不改分辨率/码率档位。 */
data class TrimRequest(
    val sourceId: String,
    val outputPath: String,
    val startMs: Long,
    val endMs: Long,
)

interface VideoTrimmer {
    fun trim(request: TrimRequest): kotlinx.coroutines.flow.Flow<VideoProgress>
    suspend fun cancel()
    suspend fun cleanupPartialFile(path: String)
}
