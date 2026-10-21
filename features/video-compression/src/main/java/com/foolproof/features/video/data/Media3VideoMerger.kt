package com.foolproof.features.video.data

import android.content.Context
import android.media.metrics.LogSessionId
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.Clock
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultAssetLoaderFactory
import androidx.media3.transformer.DefaultDecoderFactory
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
import com.foolproof.features.video.domain.MergeClip
import com.foolproof.features.video.domain.VideoCompressRequest
import com.foolproof.features.video.domain.VideoFailure
import com.foolproof.features.video.domain.VideoMerger
import com.foolproof.features.video.domain.VideoProgress
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        clips: List<MergeClip>,
        outputPath: String,
        targetShortSide: Int,
        frameRate: Int,
        bitrateKbps: Int,
    ): Flow<VideoProgress> =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // API 31 以下没有解码器回退：先逐段转码成统一格式，再拼接
            mergeLegacy(clips, outputPath, targetShortSide, frameRate, bitrateKbps)
        } else {
            mergeDirect(clips, outputPath, targetShortSide, frameRate, bitrateKbps)
        }

    private fun mergeDirect(
        clips: List<MergeClip>,
        outputPath: String,
        targetShortSide: Int,
        frameRate: Int,
        bitrateKbps: Int,
    ): Flow<VideoProgress> = callbackFlow {
        val effects = Effects(
            emptyList(),
            listOf(Presentation.createForShortSide(targetShortSide)),
        )
        val items = clips.map { clip ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(clip.source.id))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startMs)
                        .setEndPositionMs(clip.endMs)
                        .build(),
                )
                .build()
            EditedMediaItem.Builder(mediaItem)
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

        val transformerBuilder = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(encoderFactory)
            .addListener(listener)

        // 多段拼接会同时准备多个硬解码器，部分机型（如 vivo MTK）第二个解码器初始化会失败。
        // API 31+ 显式打开解码器回退：硬解失败时自动退到软解。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val decoderFactory = DefaultDecoderFactory.Builder(context)
                    .setEnableDecoderFallback(true)
                    .build()
                transformerBuilder.setAssetLoaderFactory(
                    DefaultAssetLoaderFactory(
                        context,
                        decoderFactory,
                        Clock.DEFAULT,
                        LogSessionId.LOG_SESSION_ID_NONE,
                    ),
                )
            }.onFailure {
                logUtil.w { "decoder fallback setup failed: ${it::class.simpleName}" }
            }
        }

        val transformer = transformerBuilder.build()

        activeTransformer = transformer
        logUtil.d {
            "start merge: clips=${clips.size} shortSide=$targetShortSide " +
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

    /**
     * 兼容路径：逐段单条转码（同一时刻只有一个解码器），再把统一的临时文件拼起来。
     * 代价是多一轮转码、耗时约翻倍，但老设备/硬解受限机型更稳。
     */
    private fun mergeLegacy(
        clips: List<MergeClip>,
        outputPath: String,
        targetShortSide: Int,
        frameRate: Int,
        bitrateKbps: Int,
    ): Flow<VideoProgress> = callbackFlow {
        val producer = this
        val compressor = Media3VideoCompressor(context, logUtil)
        val tempDir = File(context.cacheDir, "merge_${System.currentTimeMillis()}")
        val cancelScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val job = launch {
            try {
                if (!tempDir.exists() && !tempDir.mkdirs()) {
                    producer.trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
                    producer.close()
                    return@launch
                }

                val tempFiles = mutableListOf<String>()
                clips.forEachIndexed { index, clip ->
                    val tempPath = File(tempDir, "clip_$index.mp4").absolutePath
                    var failure: VideoFailure? = null
                    compressor.compress(
                        VideoCompressRequest(
                            sourceId = clip.source.id,
                            outputPath = tempPath,
                            targetShortSide = targetShortSide,
                            videoBitrateKbps = bitrateKbps,
                            frameRate = frameRate,
                            trimStartMs = clip.startMs,
                            trimEndMs = clip.endMs,
                            hasAudio = clip.source.hasAudio,
                        ),
                    ).collect { progress ->
                        when (progress) {
                            is VideoProgress.Running -> {
                                val overall = ((index + progress.percent / 100f) / clips.size * LEGACY_PHASE)
                                producer.trySend(VideoProgress.Running(overall.toInt().coerceIn(0, LEGACY_PHASE)))
                            }

                            is VideoProgress.Success -> Unit

                            is VideoProgress.Failed -> failure = progress.reason

                            VideoProgress.Cancelled -> failure = VideoFailure.TRANSCODE_FAILED
                        }
                    }
                    if (failure != null) {
                        producer.trySend(VideoProgress.Failed(failure))
                        producer.close()
                        return@launch
                    }
                    tempFiles += tempPath
                }

                val items = tempFiles.map { path ->
                    EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(File(path))))
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
                        if (file.exists()) {
                            producer.trySend(VideoProgress.Success(file.absolutePath, bytes))
                        } else {
                            producer.trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
                        }
                        producer.close()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exception: ExportException,
                    ) {
                        logUtil.w {
                            "legacy merge failed: code=${exception.errorCode} " +
                                "${exception::class.simpleName}: ${exception.message}"
                        }
                        producer.trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
                        producer.close()
                    }
                }

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(listener)
                    .build()
                activeTransformer = transformer
                transformer.start(composition, outputPath)

                val poller = launch {
                    val holder = ProgressHolder()
                    while (isActive) {
                        delay(PROGRESS_POLL_INTERVAL_MS)
                        val state = runCatching { transformer.getProgress(holder) }.getOrNull()
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val overall = LEGACY_PHASE +
                                holder.progress * (100 - LEGACY_PHASE) / 100
                            producer.trySend(VideoProgress.Running(overall.coerceIn(0, 100)))
                        }
                    }
                }
                poller.join()
            } catch (throwable: Throwable) {
                logUtil.w { "legacy merge crashed: ${throwable::class.simpleName}" }
                producer.trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
                producer.close()
            } finally {
                tempDir.deleteRecursively()
            }
        }

        awaitClose {
            job.cancel()
            cancelScope.launch { compressor.cancel() }
            activeTransformer?.cancel()
            activeTransformer = null
            runCatching { tempDir.deleteRecursively() }
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
        /** 逐段转码占总进度的比例，剩下的给最终拼接。 */
        const val LEGACY_PHASE = 70
    }

    @Suppress("unused")
    private fun assertMainThread() {
        check(Looper.myLooper() == mainHandler.looper) { "Transformer 调用必须在主线程" }
    }
}
