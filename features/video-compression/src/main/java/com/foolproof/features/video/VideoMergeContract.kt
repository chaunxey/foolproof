package com.foolproof.features.video

import com.foolproof.base.mvi.UiAction
import com.foolproof.base.mvi.UiEffect
import com.foolproof.base.mvi.UiState
import com.foolproof.features.video.domain.SourceVideo

/** 视频拼接页状态。 */
data class VideoMergeUiState(
    val videos: List<SourceVideo> = emptyList(),
    val isMerging: Boolean = false,
    val progress: Int = 0,
    val output: CompressedVideo? = null,
    /** API ≥ 29 才显示【存相册】。 */
    val saveSupported: Boolean = false,
) : UiState {

    val totalDurationMs: Long
        get() = videos.sumOf { it.durationMs }

    val canStart: Boolean
        get() = videos.size >= 2 && !isMerging
}

sealed interface VideoMergeAction : UiAction {
    data class VideosPicked(val ids: List<String>) : VideoMergeAction
    data class Remove(val id: String) : VideoMergeAction
    data class MoveUp(val id: String) : VideoMergeAction

    data object Start : VideoMergeAction
    data object Cancel : VideoMergeAction
    data object Share : VideoMergeAction
    data object SaveToGallery : VideoMergeAction
    data object Reset : VideoMergeAction
}

sealed interface VideoMergeEffect : UiEffect {
    data class Message(val kind: MessageKind) : VideoMergeEffect
    data class Share(val path: String) : VideoMergeEffect

    enum class MessageKind {
        NEED_TWO,
        TOO_MANY,
        READ_FAILED,
        MERGE_FAILED,
        CANCELLED,
        DONE,
        SAVED,
        SAVE_FAILED,
        SAVE_UNSUPPORTED,
    }
}
