package com.foolproof.features.video

import com.foolproof.base.mvi.UiAction
import com.foolproof.base.mvi.UiState
import com.foolproof.features.video.domain.SourceVideo
import com.foolproof.features.video.domain.TrimRange

data class VideoTrimUiState(
    val video: SourceVideo? = null,
    val trim: TrimRange = TrimRange(0L, 0L),
    val phase: VideoPhase = VideoPhase.Idle,
    val output: CompressedVideo? = null,
    val saveSupported: Boolean = false,
) : UiState {

    val isTrimming: Boolean
        get() = phase is VideoPhase.Compressing

    val trimDurationMs: Long
        get() = if (video == null) 0L else trim.durationMs

    val canStart: Boolean
        get() = video != null && !isTrimming && trimDurationMs >= MIN_TRIM_DURATION_MS
}

sealed interface VideoTrimAction : UiAction {
    data class VideoPicked(val id: String) : VideoTrimAction
    data class TrimChanged(val startMs: Long, val endMs: Long) : VideoTrimAction
    data object Start : VideoTrimAction
    data object Cancel : VideoTrimAction
    data object Reset : VideoTrimAction
    data object Share : VideoTrimAction
    data object SaveToGallery : VideoTrimAction
}

const val MIN_TRIM_DURATION_MS = 500L
