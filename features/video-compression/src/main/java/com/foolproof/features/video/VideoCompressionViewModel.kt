package com.foolproof.features.video

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.base.util.OutputNaming
import com.foolproof.features.video.domain.CompressionPreset
import com.foolproof.features.video.domain.EncoderCatalog
import com.foolproof.features.video.domain.ResolutionTier
import com.foolproof.features.video.domain.TrimRange
import com.foolproof.features.video.domain.VideoCompressRequest
import com.foolproof.features.video.domain.VideoCompressor
import com.foolproof.features.video.domain.VideoExporter
import com.foolproof.features.video.domain.VideoFailure
import com.foolproof.features.video.domain.VideoProgress
import com.foolproof.features.video.domain.VideoReader
import java.io.File
import kotlinx.coroutines.Job

/**
 * 视频压缩的 ViewModel（宪法 §4）。
 *
 * 只依赖接口：[VideoReader] / [VideoCompressor] / [EncoderCatalog]，
 * 平台对象全部挡在 `data/` 里，因此本类可在 JVM 单测。
 */
class VideoCompressionViewModel(
    private val reader: VideoReader,
    private val compressor: VideoCompressor,
    private val encoderCatalog: EncoderCatalog,
    private val exporter: VideoExporter,
    private val outputDir: File,
    private val log: LogUtil,
) : BaseViewModel<VideoUiState, VideoEffect>(VideoUiState(), log) {

    private var compressJob: Job? = null
    private var currentOutputPath: String? = null

    /** 本实例创建时间：早于它的输出文件都是上一个进程留下的孤儿。 */
    private val createdAtMs = System.currentTimeMillis()

    init {
        // API ≥ 29 才允许无权限写相册（FR-013）：低版本直接不暴露该入口
        updateState { it.copy(saveSupported = exporter.canSaveToGallery()) }
        // 进程被杀时取消/失败清理都来不及执行，启动时扫一遍上一进程的残留（FR-014）
        cleanupOrphanOutputs()
    }

    private fun cleanupOrphanOutputs() {
        launch {
            runCatching {
                outputDir.listFiles()?.forEach { file ->
                    if (file.isFile && file.lastModified() < createdAtMs) {
                        log.d { "cleanup orphan output: ${file.name}" }
                        file.delete()
                    }
                }
            }
        }
    }

    override fun onAction(action: UiAction) {
        when (action) {
            is VideoAction.VideoPicked -> loadVideo(action.id)

            is VideoAction.TrimChanged -> {
                val video = currentState.video ?: return
                val range = TrimRange.create(action.startMs, action.endMs, video.durationMs)
                updateState { it.copy(trim = range) }
            }

            is VideoAction.ResolutionChanged -> updateState { state ->
                val sourceShortSide = state.video?.shortSide ?: Int.MAX_VALUE
                state.copy(preset = state.preset.copy(resolution = cappedResolution(action.tier, sourceShortSide)))
            }

            is VideoAction.BitrateChanged -> updateState {
                it.copy(preset = it.preset.copy(bitrate = action.tier))
            }

            is VideoAction.TargetSizeChanged -> updateState { state ->
                state.copy(
                    targetSizeMb = action.mb?.coerceIn(1, state.maxTargetMb),
                )
            }

            VideoAction.Start -> startCompression()

            VideoAction.Cancel -> cancelCompression()

            VideoAction.Reset -> reset()

            VideoAction.Share -> {
                val path = currentState.output?.path
                if (path == null) {
                    sendEffect(VideoEffect.Message(VideoEffect.MessageKind.TRANSCODE_FAILED))
                } else {
                    sendEffect(VideoEffect.Share(path))
                }
            }

            VideoAction.SaveToGallery -> saveToGallery()

            VideoAction.Reset -> updateState {
                it.copy(
                    phase = if (it.video == null) VideoPhase.Idle else VideoPhase.Ready,
                    output = null,
                )
            }
        }
    }

    // region 选择视频

    private fun loadVideo(id: String) {
        updateState { it.copy(phase = VideoPhase.Loading, output = null) }

        launch {
            val video = try {
                reader.read(id)
            } catch (throwable: Throwable) {
                log.w { "read video failed: ${throwable::class.simpleName}: ${throwable.message}" }
                updateState { it.copy(phase = VideoPhase.Failed(VideoFailure.SOURCE_UNREADABLE)) }
                sendEffect(VideoEffect.Message(VideoEffect.MessageKind.SOURCE_UNREADABLE))
                return@launch
            }

            val avc = encoderCatalog.findEncoder(VIDEO_AVC)
            if (avc == null) {
                updateState { it.copy(video = video, phase = VideoPhase.Failed(VideoFailure.NO_ENCODER)) }
                sendEffect(VideoEffect.Message(VideoEffect.MessageKind.NO_ENCODER))
                return@launch
            }

            if (!avc.isHardware) {
                // 不静默：如实告知只有软件编码器（模拟器即此情况）
                sendEffect(VideoEffect.Message(VideoEffect.MessageKind.SOFTWARE_ENCODER_ONLY))
            }

            // 档位上限：编码器能力 + 源分辨率（不放大）。两者取小后自动降档，避免界面显示 720p 却按 480p 编码。
            val cappedPreset = currentState.preset.copy(
                resolution = cappedResolution(currentState.preset.resolution, video.shortSide),
            )

            updateState {
                val sourceMb = maxOf(1, (video.sizeBytes / (1024L * 1024L)).toInt())
                it.copy(
                    video = video,
                    trim = TrimRange.full(video.durationMs),
                    preset = cappedPreset,
                    targetSizeMb = it.targetSizeMb?.coerceIn(1, sourceMb),
                    phase = VideoPhase.Ready,
                    encoderNote = if (avc.isHardware) null else EncoderNote.SOFTWARE_ONLY,
                )
            }

            // 超长/超大：提示建议先裁剪，但仍允许继续（规格「边界情况」）
            if (video.durationMs > LONG_VIDEO_MS || video.sizeBytes > LONG_VIDEO_BYTES) {
                sendEffect(VideoEffect.Message(VideoEffect.MessageKind.LONG_VIDEO))
            }
        }
    }

    // endregion

    // region 导出

    private fun saveToGallery() {
        val output = currentState.output
        if (output == null) {
            sendEffect(VideoEffect.Message(VideoEffect.MessageKind.SAVE_FAILED))
            return
        }
        if (!exporter.canSaveToGallery()) {
            sendEffect(VideoEffect.Message(VideoEffect.MessageKind.SAVE_UNSUPPORTED))
            return
        }

        launch {
            val name = File(output.path).nameWithoutExtension
            val ok = exporter.saveToGallery(output.path, name)
            sendEffect(
                VideoEffect.Message(
                    if (ok) VideoEffect.MessageKind.SAVED else VideoEffect.MessageKind.SAVE_FAILED,
                ),
            )
        }
    }

    // endregion

    /**
     * 把请求的档位夹到「编码器能编码」且「不超过源分辨率」的范围内。
     *
     * @param sourceShortSide 源视频短边；档位高于它时降档（不放大）
     */
    private fun cappedResolution(requested: ResolutionTier, sourceShortSide: Int): ResolutionTier {
        val encoderMax = encoderCatalog.findEncoder(VIDEO_AVC)?.maxShortSide ?: Int.MAX_VALUE
        val limit = minOf(encoderMax, if (sourceShortSide > 0) sourceShortSide else Int.MAX_VALUE)
        if (requested.shortSide <= limit) return requested
        return ResolutionTier.entries
            .filter { it.shortSide <= limit }
            .maxByOrNull { it.shortSide }
            ?: ResolutionTier.P480
    }

    // region 压缩

    private fun startCompression() {
        val state = currentState
        val video = state.video ?: return
        if (state.isCompressing) return

        val estimate = state.estimatedBytes
        if (!outputDir.exists()) outputDir.mkdirs()
        if (estimate > 0 && outputDir.usableSpace in 1 until estimate) {
            updateState { it.copy(phase = VideoPhase.Failed(VideoFailure.OUT_OF_SPACE)) }
            sendEffect(VideoEffect.Message(VideoEffect.MessageKind.OUT_OF_SPACE))
            return
        }

        val avc = encoderCatalog.findEncoder(VIDEO_AVC)
        val maxSize = avc?.maxShortSide ?: Int.MAX_VALUE
        val targetShortSide = state.preset.targetShortSide(video, maxSize)
        val frameRate = minOf(video.frameRate, CompressionPreset.MAX_FRAME_RATE)

        // 新压缩会替换上一个结果（output 随即置空），旧产物从此不可达 → 先删掉，
        // 避免同一会话里反复压缩把 files/videos/ 撑满（FR-014：不得留下孤立文件）
        runCatching {
            outputDir.listFiles()?.forEach { file ->
                if (file.isFile && file.delete()) {
                    log.d { "removed previous output: ${file.name}" }
                }
            }
        }

        val outputPath = File(
            outputDir,
            OutputNaming.fileName(
                OutputNaming.MediaType.VIDEO,
                OutputNaming.Action.COMPRESS,
                "mp4",
            ),
        ).absolutePath
        currentOutputPath = outputPath

        val targetBytes = state.targetSizeMb?.let { it * 1024L * 1024L }
        val request = VideoCompressRequest(
            sourceId = video.id,
            outputPath = outputPath,
            targetShortSide = targetShortSide,
            videoBitrateKbps = if (targetBytes != null) {
                CompressionPreset.targetVideoBitrateKbps(
                    targetBytes = targetBytes,
                    durationMs = state.trim.durationMs,
                    hasAudio = video.hasAudio,
                )
            } else {
                state.preset.videoBitrateKbps()
            },
            frameRate = frameRate,
            trimStartMs = state.trim.startMs,
            trimEndMs = state.trim.endMs,
            hasAudio = video.hasAudio,
        )

        updateState { it.copy(phase = VideoPhase.Compressing(0), output = null) }

        compressJob = launchJob {
            var bitrate = request.videoBitrateKbps
            var attempt = 0
            while (attempt < MAX_TARGET_ATTEMPTS) {
                attempt++
                var success: VideoProgress.Success? = null
                var stopped = false

                compressor.compress(request.copy(videoBitrateKbps = bitrate)).collect { progress ->
                    when (progress) {
                        is VideoProgress.Running -> updateState {
                            it.copy(phase = VideoPhase.Compressing(progress.percent))
                        }

                        is VideoProgress.Success -> success = progress

                        is VideoProgress.Failed -> {
                            // 失败也必须清掉半成品，否则 files/videos/ 会留下不完整文件（FR-014）
                            currentOutputPath?.let { compressor.cleanupPartialFile(it) }
                            updateState { it.copy(phase = VideoPhase.Failed(progress.reason)) }
                            sendEffect(VideoEffect.Message(progress.reason.toMessage()))
                            stopped = true
                        }

                        VideoProgress.Cancelled -> {
                            updateState { it.copy(phase = VideoPhase.Ready) }
                            sendEffect(VideoEffect.Message(VideoEffect.MessageKind.CANCELLED))
                            stopped = true
                        }
                    }
                }
                if (stopped) return@launchJob

                val result = success ?: return@launchJob
                val target = targetBytes
                val overTarget = target != null && result.bytes > target
                if (!overTarget || attempt >= MAX_TARGET_ATTEMPTS) {
                    val sourceBytes = currentState.video?.sizeBytes ?: 0L
                    updateState {
                        it.copy(
                            phase = VideoPhase.Done,
                            output = CompressedVideo(result.path, result.bytes),
                        )
                    }
                    sendEffect(VideoEffect.Message(VideoEffect.MessageKind.DONE))
                    // 源本身很小时，当前档位的码率可能高于它，结果反而更大——如实告知
                    if (sourceBytes > 0 && result.bytes >= sourceBytes) {
                        sendEffect(VideoEffect.Message(VideoEffect.MessageKind.LARGER_THAN_SOURCE))
                    }
                    return@launchJob
                }

                // 超出目标体积：按实际/目标比例降码率，删掉本次产物后重试
                File(outputPath).delete()
                bitrate = (bitrate.toLong() * target!! / result.bytes * 95 / 100)
                    .toInt()
                    .coerceAtLeast(CompressionPreset.MIN_TARGET_KBPS)
                log.d { "target missed: ${result.bytes} > $target, retry at $bitrate kbps" }
                updateState { it.copy(phase = VideoPhase.Compressing(0)) }
            }
        }
    }

    /** 重置：取消进行中的任务并回到初始状态；已生成的产物保留（重置是界面动作，不删数据）。 */
    private fun reset() {
        val wasCompressing = currentState.isCompressing
        val path = currentOutputPath
        compressJob?.cancel()
        compressJob = null
        launch {
            if (wasCompressing) {
                compressor.cancel()
                path?.let { compressor.cleanupPartialFile(it) }
            }
            updateState { VideoUiState(saveSupported = it.saveSupported) }
        }
    }

    private fun cancelCompression() {
        val path = currentOutputPath
        compressJob?.cancel()
        compressJob = null

        launch {
            compressor.cancel()
            path?.let { compressor.cleanupPartialFile(it) }
            updateState { it.copy(phase = VideoPhase.Ready) }
            sendEffect(VideoEffect.Message(VideoEffect.MessageKind.CANCELLED))
        }
    }

    // endregion

    private fun VideoFailure.toMessage(): VideoEffect.MessageKind = when (this) {
        VideoFailure.SOURCE_UNREADABLE -> VideoEffect.MessageKind.SOURCE_UNREADABLE
        VideoFailure.NO_ENCODER -> VideoEffect.MessageKind.NO_ENCODER
        VideoFailure.OUT_OF_SPACE -> VideoEffect.MessageKind.OUT_OF_SPACE
        VideoFailure.TRANSCODE_FAILED, VideoFailure.UNKNOWN -> VideoEffect.MessageKind.TRANSCODE_FAILED
    }

    private companion object {
        const val VIDEO_AVC = "video/avc"

        /** 目标体积最多尝试 3 次（首轮 + 两次降码率）。 */
        const val MAX_TARGET_ATTEMPTS = 3

        /** 超过 30 分钟建议先裁剪（规格「边界情况」）。 */
        const val LONG_VIDEO_MS = 30 * 60 * 1000L

        /** 超过 2GB 建议先裁剪。 */
        const val LONG_VIDEO_BYTES = 2L * 1024 * 1024 * 1024
    }
}
