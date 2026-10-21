package com.foolproof.features.video.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.domain.VideoCompressRequest
import com.foolproof.features.video.domain.VideoCompressor
import com.foolproof.features.video.domain.VideoFailure
import com.foolproof.features.video.domain.VideoProgress
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 基于 Media3 Transformer 的压缩实现（宪法 §2 v1.13）。
 *
 * 三个来自实测/API 约束的实现细节：
 *
 * 1. **进度靠轮询**：Media3 1.11 的 `Transformer.Listener` 没有 `onProgress`，
 *    只能用 `Transformer.getProgress(ProgressHolder)` 定时取（本实现 400ms 一次）。
 * 2. **必须在有 Looper 的线程调用**：`start()` 与 `getProgress()` 都在主线程执行
 *    （`flowOn(Dispatchers.Main.immediate)`）。
 * 3. **取消异步**：`cancel()` 返回时文件可能还在写，所以 [cleanupPartialFile] 会重试删除。
 */
class Media3VideoCompressor(
    private val context: Context,
    private val logUtil: LogUtil,
) : VideoCompressor {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null

    override fun compress(request: VideoCompressRequest): Flow<VideoProgress> = callbackFlow {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(request.sourceId))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(request.trimStartMs)
                    .setEndPositionMs(request.trimEndMs)
                    .build(),
            )
            .build()

        val effects = Effects(
            emptyList(),
            listOf(Presentation.createForShortSide(request.targetShortSide)),
        )

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .setFrameRate(request.frameRate)
            .apply { if (!request.hasAudio) setRemoveAudio(true) }
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(request.videoBitrateKbps * 1000)
                    .build(),
            )
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                // ExportResult 不含输出路径（只有 fileSizeBytes），路径以我们传入的为准
                val file = File(request.outputPath)
                val bytes = if (exportResult.fileSizeBytes > 0) {
                    exportResult.fileSizeBytes
                } else {
                    file.length()
                }
                if (!file.exists()) {
                    logUtil.w { "transcode reported success but output missing" }
                    trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
                } else {
                    trySend(VideoProgress.Success(path = file.absolutePath, bytes = bytes))
                }
                close()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exception: ExportException,
            ) {
                logUtil.w {
                    "transcode failed: code=${exception.errorCode} " +
                        "${exception::class.simpleName}: ${exception.message}"
                }
                trySend(VideoProgress.Failed(mapFailure(exception)))
                close()
            }
        }

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(listener)
            .build()

        activeTransformer = transformer
        logUtil.d {
            "start transcode: shortSide=${request.targetShortSide} " +
                "bitrate=${request.videoBitrateKbps}kbps fps=${request.frameRate} " +
                "trim=${request.trimStartMs}..${request.trimEndMs}"
        }

        runCatching { transformer.start(editedMediaItem, request.outputPath) }
            .onFailure { throwable ->
                logUtil.w { "transformer.start failed: ${throwable::class.simpleName}" }
                trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
                close()
            }

        val poller = launch {
            val holder = ProgressHolder()
            while (isActive) {
                delay(PROGRESS_POLL_INTERVAL_MS)
                val state = runCatching { transformer.getProgress(holder) }.getOrNull()
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(VideoProgress.Running(holder.progress.coerceIn(0, 100)))
                }
            }
        }

        awaitClose {
            poller.cancel()
            // 收集方取消即视为取消压缩；这里只发信号，文件清理由 cleanupPartialFile 负责
            transformer.cancel()
            activeTransformer = null
        }
    }.flowOn(Dispatchers.Main.immediate)

    override suspend fun cancel() {
        withContext(Dispatchers.Main) {
            activeTransformer?.cancel()
        }
    }

    /**
     * 删除半成品：取消是异步的，文件可能仍被持有，所以重试若干次再放弃。
     */
    override suspend fun cleanupPartialFile(path: String) {
        repeat(CLEANUP_ATTEMPTS) { attempt ->
            val file = File(path)
            if (!file.exists()) return
            if (file.delete()) return
            delay(CLEANUP_RETRY_DELAY_MS)
            if (attempt == CLEANUP_ATTEMPTS - 1) {
                logUtil.w { "partial file still present after retries: ${file.name}" }
            }
        }
    }

    /**
     * 错误码映射（Media3 的 `ExportException` 没有"空间不足"专用码，
     * 空间问题在 ViewModel 里按预估体积预检，这里只处理转码侧的错误）。
     */
    private fun mapFailure(exception: ExportException): VideoFailure = when (exception.errorCode) {
        ExportException.ERROR_CODE_IO_FILE_NOT_FOUND,
        ExportException.ERROR_CODE_IO_NO_PERMISSION,
        ExportException.ERROR_CODE_DECODING_FAILED,
        ExportException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> VideoFailure.SOURCE_UNREADABLE

        ExportException.ERROR_CODE_ENCODER_INIT_FAILED,
        ExportException.ERROR_CODE_ENCODING_FAILED,
        ExportException.ERROR_CODE_ENCODING_FORMAT_UNSUPPORTED,
        ExportException.ERROR_CODE_DECODER_INIT_FAILED,
        -> VideoFailure.NO_ENCODER

        else -> VideoFailure.TRANSCODE_FAILED
    }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MS = 400L
        const val CLEANUP_ATTEMPTS = 10
        const val CLEANUP_RETRY_DELAY_MS = 100L
    }

    /** 保留给未来的主线程工具方法：确认当前是否主线程（调试用）。 */
    private fun assertMainThread() {
        check(Looper.myLooper() == mainHandler.looper) { "Transformer 调用必须在主线程" }
    }
}
