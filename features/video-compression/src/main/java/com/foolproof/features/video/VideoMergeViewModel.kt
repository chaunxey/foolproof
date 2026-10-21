package com.foolproof.features.video

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.features.video.domain.MergeClip
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
                it.copy(clips = it.clips.filterNot { clip -> clip.source.id == action.id })
            }
            is VideoMergeAction.MoveUp -> moveUp(action.id)
            is VideoMergeAction.MoveTo -> moveTo(action.id, action.toIndex)
            is VideoMergeAction.TrimChanged -> trim(action.id, action.startMs, action.endMs)
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
            val room = (MAX_VIDEOS - currentState.clips.size).coerceAtLeast(0)
            val existing = currentState.clips.map { it.source.id }.toSet()
            // 同一视频重复添加会让 id 冲突（拖拽/删除按 id 定位），这里按 id 去重
            val fresh = loaded.filter { it.id !in existing }.distinctBy { it.id }
            val seedBase = (currentState.clips.maxOfOrNull { it.colorSeed } ?: -1) + 1
            val accepted = fresh.take(room).mapIndexed { index, source ->
                MergeClip(source = source, colorSeed = seedBase + index)
            }
            if (accepted.isNotEmpty()) {
                updateState { it.copy(clips = it.clips + accepted) }
            }

            val duplicateCount = loaded.size - fresh.size
            when {
                accepted.isEmpty() && duplicateCount > 0 -> {
                    // 选中的都是列表里已有的视频，给个明确提示，避免“选了没反应”
                    sendEffect(
                        VideoMergeEffect.Message(VideoMergeEffect.MessageKind.ALREADY_ADDED),
                    )
                }
                accepted.size < fresh.size -> {
                    sendEffect(VideoMergeEffect.Message(VideoMergeEffect.MessageKind.TOO_MANY))
                }
                duplicateCount > 0 -> {
                    sendEffect(
                        VideoMergeEffect.Message(
                            VideoMergeEffect.MessageKind.DUPLICATES_SKIPPED,
                        ),
                    )
                }
            }
        }
    }

    private fun moveTo(id: String, toIndex: Int) {
        updateState { state ->
            val from = state.clips.indexOfFirst { it.source.id == id }
            if (from < 0 || toIndex !in state.clips.indices || from == toIndex) {
                state
            } else {
                val list = state.clips.toMutableList()
                list.add(toIndex, list.removeAt(from))
                state.copy(clips = list)
            }
        }
    }

    private fun moveUp(id: String) {
        updateState { state ->
            val index = state.clips.indexOfFirst { it.source.id == id }
            if (index <= 0) {
                state
            } else {
                val list = state.clips.toMutableList()
                val item = list.removeAt(index)
                list.add(index - 1, item)
                state.copy(clips = list)
            }
        }
    }

    /** 逐段裁剪：夹紧到 [0, duration]，且至少保留 [MIN_CLIP_MS]。 */
    private fun trim(id: String, startMs: Long, endMs: Long) {
        updateState { state ->
            val index = state.clips.indexOfFirst { it.source.id == id }
            if (index < 0) return@updateState state
            val clip = state.clips[index]
            val duration = clip.source.durationMs
            if (duration <= MIN_CLIP_MS) return@updateState state

            val start = startMs.coerceIn(0L, duration - MIN_CLIP_MS)
            val end = endMs.coerceIn(start + MIN_CLIP_MS, duration)
            val updated = clip.copy(startMs = start, endMs = end)
            state.copy(clips = state.clips.toMutableList().also { it[index] = updated })
        }
    }

    private fun start() {
        if (currentState.isMerging) return
        if (currentState.clips.size < 2) {
            sendEffect(VideoMergeEffect.Message(VideoMergeEffect.MessageKind.NEED_TWO))
            return
        }

        val path = File(outputDir, "merge_${System.currentTimeMillis()}.mp4").absolutePath
        outputPath = path
        updateState { it.copy(isMerging = true, progress = 0, output = null) }

        mergeJob = launchJob {
            merger.merge(
                clips = currentState.clips,
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
                                outputDurationMs = it.clips.sumOf { clip -> clip.durationMs },
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
        const val MIN_CLIP_MS = 500L
        const val TARGET_SHORT_SIDE = 720
        const val TARGET_FPS = 30
        const val TARGET_BITRATE_KBPS = 4_000
    }
}
