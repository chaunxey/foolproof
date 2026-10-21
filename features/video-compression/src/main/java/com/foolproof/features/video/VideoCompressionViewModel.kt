package com.foolproof.features.video

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.features.video.domain.CompressionPreset
import com.foolproof.features.video.domain.EncoderCatalog
import com.foolproof.features.video.domain.ResolutionTier
import com.foolproof.features.video.domain.TrimRange
import com.foolproof.features.video.domain.VideoCompressRequest
import com.foolproof.features.video.domain.VideoCompressor
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
    private val outputDir: File,
    private val log: LogUtil,
) : BaseViewModel<VideoUiState, VideoEffect>(VideoUiState(), log) {

    private var compressJob: Job? = null
    private var currentOutputPath: String? = null

    override fun onAction(action: UiAction) {
        when (action) {
            is VideoAction.VideoPicked -> loadVideo(action.id)

            is VideoAction.TrimChanged -> {
                val video = currentState.video ?: return
                val range = TrimRange.create(action.startMs, action.endMs, video.durationMs)
                updateState { it.copy(trim = range) }
            }

            is VideoAction.ResolutionChanged -> updateState {
                it.copy(preset = it.preset.copy(resolution = cappedResolution(action.tier)))
            }

            is VideoAction.BitrateChanged -> updateState {
                it.copy(preset = it.preset.copy(bitrate = action.tier))
            }

            VideoAction.Start -> startCompression()

            VideoAction.Cancel -> cancelCompression()

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

            // 档位上限受编码器能力约束（编码器只到 720p 时，1080p 档自动降级）
            val cappedPreset = currentState.preset.copy(
                resolution = cappedResolution(currentState.preset.resolution),
            )

            updateState {
                it.copy(
                    video = video,
                    trim = TrimRange.full(video.durationMs),
                    preset = cappedPreset,
                    phase = VideoPhase.Ready,
                    encoderNote = if (avc.isHardware) null else "当前设备无硬件编码器，压缩会很慢",
                )
            }
        }
    }

    // endregion

    /** 把请求的档位夹到编码器能编码的最大短边以内。 */
    private fun cappedResolution(requested: ResolutionTier): ResolutionTier {
        val maxShortSide = encoderCatalog.findEncoder(VIDEO_AVC)?.maxShortSide ?: return requested
        if (requested.shortSide <= maxShortSide) return requested
        return ResolutionTier.entries
            .filter { it.shortSide <= maxShortSide }
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

        val outputPath = File(outputDir, "compressed_${System.currentTimeMillis()}.mp4").absolutePath
        currentOutputPath = outputPath

        val request = VideoCompressRequest(
            sourceId = video.id,
            outputPath = outputPath,
            targetShortSide = targetShortSide,
            videoBitrateKbps = state.preset.videoBitrateKbps(),
            frameRate = frameRate,
            trimStartMs = state.trim.startMs,
            trimEndMs = state.trim.endMs,
            hasAudio = video.hasAudio,
        )

        updateState { it.copy(phase = VideoPhase.Compressing(0), output = null) }

        compressJob = launchJob {
            compressor.compress(request).collect { progress ->
                when (progress) {
                    is VideoProgress.Running -> updateState {
                        it.copy(phase = VideoPhase.Compressing(progress.percent))
                    }

                    is VideoProgress.Success -> {
                        updateState {
                            it.copy(
                                phase = VideoPhase.Done,
                                output = CompressedVideo(progress.path, progress.bytes),
                            )
                        }
                        sendEffect(VideoEffect.Message(VideoEffect.MessageKind.DONE))
                    }

                    is VideoProgress.Failed -> {
                        updateState { it.copy(phase = VideoPhase.Failed(progress.reason)) }
                        sendEffect(VideoEffect.Message(progress.reason.toMessage()))
                    }

                    VideoProgress.Cancelled -> {
                        updateState { it.copy(phase = VideoPhase.Ready) }
                        sendEffect(VideoEffect.Message(VideoEffect.MessageKind.CANCELLED))
                    }
                }
            }
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
    }
}
