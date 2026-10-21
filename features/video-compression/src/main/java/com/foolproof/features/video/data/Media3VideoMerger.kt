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
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.domain.SourceVideo
import com.foolproof.features.video.domain.VideoFailure
import com.foolproof.features.video.domain.VideoMerger
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
 * Media3 Transformer 的拼接实现。
 *
 * 把多段视频放进同一个 [EditedMediaItemSequence]，Transformer 会按顺序首尾相接转码。
 * 无音轨的片段用 `experimentalSetForceAudioTrack(true)` 补静音，避免拼接处音画不同步。
 */
class Media3VideoMerger(
    private val context: Context,
    private val logUtil: LogUtil,
) : VideoMerger {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var activeTransformer: Transformer? = null

    override fun merge(
        videos: List<SourceVideo>,
        outputPath: String,
        targetShortSide: Int,
        frameRate: Int,
        bitrateKbps: Int,
    ): Flow<VideoProgress> = callbackFlow {
        val effects = Effects(
            emptyList(),
            listOf(Presentation.createForShortSide(targetShortSide)),
        )
        val items = videos.map { video ->
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.parse(video.id)))
                .setEffects(effects)
                .setFrameRate(frameRate)
                .build()
        }
        val sequence = EditedMediaItemSequence.Builder(items)
            .experimentalSetForceAudioTrack(true)
            .build()
        val composition = Composition.Builder(listOf(sequence)).build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(bitrateKbps * 1000)
                    .build(),
            )
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val file = File(outputPath)
                val bytes = if (exportResult.fileSizeBytes > 0) {
                    exportResult.fileSizeBytes
                } else {
                    file.length()
                }
                if (!file.exists()) {
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
                    "merge failed: code=${exception.errorCode} " +
                        "${exception::class.simpleName}: ${exception.message}"
                }
                trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
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
            "start merge: clips=${videos.size} shortSide=$targetShortSide " +
                "bitrate=${bitrateKbps}kbps fps=$frameRate"
        }

        runCatching { transformer.start(composition, outputPath) }
            .onFailure { throwable ->
                logUtil.w { "merge start failed: ${throwable::class.simpleName}" }
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
            transformer.cancel()
            activeTransformer = null
        }
    }.flowOn(Dispatchers.Main.immediate)

    override suspend fun cancel() {
        withContext(Dispatchers.Main) {
            activeTransformer?.cancel()
        }
    }

    override suspend fun cleanupPartialFile(path: String) {
        repeat(CLEANUP_ATTEMPTS) { attempt ->
            val file = File(path)
            if (!file.exists()) return
            if (file.delete()) return
            delay(CLEANUP_RETRY_DELAY_MS)
            if (attempt == CLEANUP_ATTEMPTS - 1) {
                logUtil.w { "merge partial file still present: ${file.name}" }
            }
        }
    }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MS = 400L
        const val CLEANUP_ATTEMPTS = 10
        const val CLEANUP_RETRY_DELAY_MS = 100L
    }

    @Suppress("unused")
    private fun assertMainThread() {
        check(Looper.myLooper() == mainHandler.looper) { "Transformer 调用必须在主线程" }
    }
}
