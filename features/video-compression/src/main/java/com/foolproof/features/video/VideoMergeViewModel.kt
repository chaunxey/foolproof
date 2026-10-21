package com.foolproof.features.video

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.features.video.domain.VideoExporter
import com.foolproof.features.video.domain.VideoMerger
import com.foolproof.features.video.domain.VideoProgress
import com.foolproof.features.video.domain.VideoReader
import java.io.File
import kotlinx.coroutines.Job

/**
 * 视频拼接：多选 → 排序 → 首尾相接。
 *
 * v1 不做逐段裁剪；输出统一转码成 H.264/AAC 720p/30fps。
 */
class VideoMergeViewModel(
    private val reader: VideoReader,
    private val merger: VideoMerger,
    private val exporter: VideoExporter,
    private val outputDir: File,
    logger: LogUtil,
) : BaseViewModel<VideoMergeUiState, VideoMergeEffect>(
    VideoMergeUiState(saveSupported = exporter.canSaveToGallery()),
    logger,
) {

    private var mergeJob: Job? = null
    private var outputPath: String? = null

    override fun onAction(action: UiAction) {
        when (action) {
            is VideoMergeAction.VideosPicked -> addVideos(action.ids)
            is VideoMergeAction.Remove -> updateState {
                it.copy(videos = it.videos.filterNot { video -> video.id == action.id })
            }
            is VideoMergeAction.MoveUp -> moveUp(action.id)
            VideoMergeAction.Start -> start()
            VideoMergeAction.Cancel -> cancel()
            VideoMergeAction.Share -> currentState.output?.let {
                sendEffect(VideoMergeEffect.Share(it.path))
            }
            VideoMergeAction.SaveToGallery -> saveToGallery()
            VideoMergeAction.Reset -> reset()
        }
    }

    private fun addVideos(ids: List<String>) {
        if (ids.isEmpty()) return
        launch {
            val loaded = ids.mapNotNull { id -> runCatching { reader.read(id) }.getOrNull() }
            if (loaded.isEmpty()) {
                sendEffect(VideoMergeEffect.Message(VideoMergeEffect.MessageKind.READ_FAILED))
                return@launch
            }
            val room = (MAX_VIDEOS - currentState.videos.size).coerceAtLeast(0)
            val accepted = loaded.take(room)
            if (accepted.isNotEmpty()) {
                updateState { it.copy(videos = it.videos + accepted) }
            }
            if (accepted.size < loaded.size) {
                sendEffect(VideoMergeEffect.Message(VideoMergeEffect.MessageKind.TOO_MANY))
            }
        }
    }

    private fun moveUp(id: String) {
        updateState { state ->
            val index = state.videos.indexOfFirst { it.id == id }
            if (index <= 0) {
                state
            } else {
                val list = state.videos.toMutableList()
                val item = list.removeAt(index)
                list.add(index - 1, item)
                state.copy(videos = list)
            }
        }
    }

    private fun start() {
        if (currentState.isMerging) return
        if (currentState.videos.size < 2) {
            sendEffect(VideoMergeEffect.Message(VideoMergeEffect.MessageKind.NEED_TWO))
            return
        }

        val path = File(outputDir, "merge_${System.currentTimeMillis()}.mp4").absolutePath
        outputPath = path
        updateState { it.copy(isMerging = true, progress = 0, output = null) }

        mergeJob = launchJob {
            merger.merge(
                videos = currentState.videos,
                outputPath = path,
                targetShortSide = TARGET_SHORT_SIDE,
                frameRate = TARGET_FPS,
                bitrateKbps = TARGET_BITRATE_KBPS,
            ).collect { progress ->
                when (progress) {
                    is VideoProgress.Running -> updateState { it.copy(progress = progress.percent) }

                    is VideoProgress.Success -> {
                        updateState {
                            it.copy(
                                isMerging = false,
                                progress = 100,
                                output = CompressedVideo(progress.path, progress.bytes),
                            )
                        }
                        sendEffect(VideoMergeEffect.Message(VideoMergeEffect.MessageKind.DONE))
                    }

                    is VideoProgress.Failed -> {
                        updateState { it.copy(isMerging = false, progress = 0) }
                        sendEffect(VideoMergeEffect.Message(VideoMergeEffect.MessageKind.MERGE_FAILED))
                        merger.cleanupPartialFile(path)
                    }

                    VideoProgress.Cancelled -> {
                        updateState { it.copy(isMerging = false, progress = 0) }
                    }
                }
            }
        }
    }

    private fun cancel() {
        mergeJob?.cancel()
        mergeJob = null
        launch {
            merger.cancel()
            outputPath?.let { merger.cleanupPartialFile(it) }
        }
        updateState { it.copy(isMerging = false, progress = 0) }
        sendEffect(VideoMergeEffect.Message(VideoMergeEffect.MessageKind.CANCELLED))
    }

    private fun saveToGallery() {
        val output = currentState.output ?: return
        if (!exporter.canSaveToGallery()) {
            sendEffect(VideoMergeEffect.Message(VideoMergeEffect.MessageKind.SAVE_UNSUPPORTED))
            return
        }
        launch {
            val name = File(output.path).nameWithoutExtension
            val ok = exporter.saveToGallery(output.path, name)
            sendEffect(
                VideoMergeEffect.Message(
                    if (ok) VideoMergeEffect.MessageKind.SAVED else VideoMergeEffect.MessageKind.SAVE_FAILED,
                ),
            )
        }
    }

    private fun reset() {
        mergeJob?.cancel()
        mergeJob = null
        launch {
            merger.cancel()
            outputPath?.let { merger.cleanupPartialFile(it) }
            outputPath = null
        }
        updateState { VideoMergeUiState(saveSupported = it.saveSupported) }
    }

    override fun onCleared() {
        mergeJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val MAX_VIDEOS = 10
        const val TARGET_SHORT_SIDE = 720
        const val TARGET_FPS = 30
        const val TARGET_BITRATE_KBPS = 4_000
    }
}
