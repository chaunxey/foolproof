package com.foolproof.features.video.domain

/** 一次压缩请求的全部参数（纯数据，便于单测与日志）。 */
data class VideoCompressRequest(
    val sourceId: String,
    val outputPath: String,
    val targetShortSide: Int,
    val videoBitrateKbps: Int,
    val frameRate: Int,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val hasAudio: Boolean,
)

/** 压缩进度/结果事件流。 */
sealed interface VideoProgress {
    data class Running(val percent: Int) : VideoProgress
    data class Success(val path: String, val bytes: Long) : VideoProgress
    data class Failed(val reason: VideoFailure) : VideoProgress
    data object Cancelled : VideoProgress
}

/** 失败分类（spec FR-016）。 */
enum class VideoFailure {
    SOURCE_UNREADABLE,
    NO_ENCODER,
    OUT_OF_SPACE,
    TRANSCODE_FAILED,
    UNKNOWN,
}

/**
 * 视频压缩器。平台对象（`Transformer`）不得越过这个接口，否则 ViewModel 无法在 JVM 单测。
 */
interface VideoCompressor {

    /** 开始压缩；收集方取消协程即代表请求取消。 */
    fun compress(request: VideoCompressRequest): kotlinx.coroutines.flow.Flow<VideoProgress>

    /** 请求取消；具体实现需处理异步停止。 */
    suspend fun cancel()

    /**
     * 删除半成品文件。
     *
     * 之所以单独一个方法：取消是**异步**的，`Transformer.cancel()` 返回时文件可能仍在写，
     * 调用方需要"取消 → 等一会儿 → 重试删除"，这个重试逻辑放在实现里。
     */
    suspend fun cleanupPartialFile(path: String)
}
