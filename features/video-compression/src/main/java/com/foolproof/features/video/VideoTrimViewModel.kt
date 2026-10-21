package com.foolproof.features.video

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.base.util.OutputNaming
import com.foolproof.features.video.domain.TrimRange
import com.foolproof.features.video.domain.TrimRequest
import com.foolproof.features.video.domain.VideoExporter
import com.foolproof.features.video.domain.VideoFailure
import com.foolproof.features.video.domain.VideoProgress
import com.foolproof.features.video.domain.VideoReader
import com.foolproof.features.video.domain.VideoTrimmer
import java.io.File
import kotlinx.coroutines.Job

class VideoTrimViewModel(
    private val reader: VideoReader,
    private val trimmer: VideoTrimmer,
    private val exporter: VideoExporter,
    private val outputDir: File,
    private val log: LogUtil,
) : BaseViewModel<VideoTrimUiState, VideoEffect>(
    VideoTrimUiState(saveSupported = exporter.canSaveToGallery()),
    log,
) {

    private var job: Job? = null
    private var outputPath: String? = null

    override fun onAction(action: UiAction) {
        when (action) {
            is VideoTrimAction.VideoPicked -> loadVideo(action.id)
            is VideoTrimAction.TrimChanged -> {
                val video = currentState.video ?: return
                updateState {
                    it.copy(
                        trim = TrimRange.create(action.startMs, action.endMs, video.durationMs),
                        output = null,
                        phase = VideoPhase.Ready,
                    )
                }
            }
            VideoTrimAction.Start -> start()
            VideoTrimAction.Cancel -> cancel()
            VideoTrimAction.Reset -> reset()
            VideoTrimAction.Share -> currentState.output?.let {
                sendEffect(VideoEffect.Share(it.path))
            }
            VideoTrimAction.SaveToGallery -> saveToGallery()
        }
    }

    private fun loadVideo(id: String) {
        updateState { it.copy(phase = VideoPhase.Loading, output = null) }
        launch {
            val video = runCatching { reader.read(id) }.getOrElse { throwable ->
                log.w { "read video failed: ${throwable::class.simpleName}" }
                updateState { it.copy(phase = VideoPhase.Failed(VideoFailure.SOURCE_UNREADABLE)) }
                sendEffect(VideoEffect.Message(VideoEffect.MessageKind.SOURCE_UNREADABLE))
                return@launch
            }
            updateState {
                it.copy(
                    video = video,
                    trim = TrimRange.full(video.durationMs),
                    phase = VideoPhase.Ready,
                )
            }
        }
    }

    private fun start() {
        val state = currentState
        val video = state.video ?: return
        if (state.isTrimming) return
        if (state.trimDurationMs < MIN_TRIM_DURATION_MS) return

        if (!outputDir.exists()) outputDir.mkdirs()
        runCatching {
            outputDir.listFiles()?.forEach { if (it.isFile) it.delete() }
        }
        val path = File(
            outputDir,
            OutputNaming.fileName(
                OutputNaming.MediaType.VIDEO,
                OutputNaming.Action.TRIM,
                "mp4",
            ),
        ).absolutePath
        outputPath = path
        updateState { it.copy(phase = VideoPhase.Compressing(0), output = null) }

        job = launchJob {
            trimmer.trim(
                TrimRequest(
                    sourceId = video.id,
                    outputPath = path,
                    startMs = state.trim.startMs,
                    endMs = state.trim.endMs,
                ),
            ).collect { progress ->
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
                        path.let { trimmer.cleanupPartialFile(it) }
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

    private fun cancel() {
        job?.cancel()
        job = null
        launch {
            trimmer.cancel()
            outputPath?.let { trimmer.cleanupPartialFile(it) }
            updateState { it.copy(phase = VideoPhase.Ready) }
            sendEffect(VideoEffect.Message(VideoEffect.MessageKind.CANCELLED))
        }
    }

    private fun reset() {
        job?.cancel()
        job = null
        launch {
            trimmer.cancel()
            outputPath?.let { trimmer.cleanupPartialFile(it) }
            updateState { VideoTrimUiState(saveSupported = exporter.canSaveToGallery()) }
        }
    }

    private fun saveToGallery() {
        val output = currentState.output ?: return
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

    private fun VideoFailure.toMessage(): VideoEffect.MessageKind = when (this) {
        VideoFailure.SOURCE_UNREADABLE -> VideoEffect.MessageKind.SOURCE_UNREADABLE
        VideoFailure.NO_ENCODER -> VideoEffect.MessageKind.NO_ENCODER
        VideoFailure.OUT_OF_SPACE -> VideoEffect.MessageKind.OUT_OF_SPACE
        VideoFailure.TRANSCODE_FAILED, VideoFailure.UNKNOWN -> VideoEffect.MessageKind.TRANSCODE_FAILED
    }
}
