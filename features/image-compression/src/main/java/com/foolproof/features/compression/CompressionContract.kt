package com.foolproof.features.compression

import com.foolproof.base.mvi.UiAction
import com.foolproof.base.mvi.UiEffect
import com.foolproof.base.mvi.UiState
import com.foolproof.features.compression.domain.CompressionOption
import com.foolproof.features.compression.domain.CompressionOutcome
import com.foolproof.features.compression.domain.OutcomeStatus
import com.foolproof.features.compression.domain.OutputFormat
import com.foolproof.features.compression.domain.SourceImage

/** 图片压缩页面的状态。 */
data class CompressionUiState(
    val images: List<SourceImage> = emptyList(),
    val option: CompressionOption = CompressionOption.Default,
    val outcomes: Map<String, CompressionOutcome> = emptyMap(),
    val isRunning: Boolean = false,
    val estimateBytes: Long? = null,
    val estimateLoading: Boolean = false,
    /** 当前系统能否直接保存到相册（API ≥ 29） */
    val saveSupported: Boolean = false,
) : UiState {

    val canStart: Boolean
        get() = images.isNotEmpty() && !isRunning

    val completedCount: Int
        get() = outcomes.values.count {
            it.status == OutcomeStatus.SUCCESS || it.status == OutcomeStatus.NO_GAIN
        }

    /** 目标体积上限：不能达到或超过最小的原图，否则不叫压缩。 */
    val maxTargetKb: Int?
        get() = images.minOfOrNull { it.sizeBytes }?.let { bytes ->
            ((bytes - 1) / 1024L).toInt().coerceAtLeast(5)
        }

    /** 实际节省的总字节数（只统计压缩成功的项）。 */
    val totalSavedBytes: Long
        get() = outcomes.values.sumOf { outcome ->
            val after = outcome.compressedBytes ?: return@sumOf 0L
            (outcome.originalBytes - after).coerceAtLeast(0L)
        }

    fun outcomeOf(id: String): CompressionOutcome =
        outcomes[id] ?: CompressionOutcome(id = id)
}

/** UI 发往 ViewModel 的意图。 */
sealed interface CompressionAction : UiAction {

    data object SelectImages : CompressionAction

    data class ImagesPicked(val ids: List<String>) : CompressionAction

    data object Start : CompressionAction
    data object Reset : CompressionAction

    data object Cancel : CompressionAction

    data class Retry(val id: String) : CompressionAction

    data class ChangeFormat(val format: OutputFormat) : CompressionAction

    data class ChangeQuality(val quality: Int) : CompressionAction

    data class ChangeTargetBytes(val bytes: Long?) : CompressionAction

    data class Share(val id: String) : CompressionAction

    data class SaveToGallery(val id: String) : CompressionAction

    data object Clear : CompressionAction
}

/** ViewModel 发往 UI 的一次性事件。 */
sealed interface CompressionEffect : UiEffect {

    /** 请求打开系统照片选择器（ViewModel 不持有 Activity）。 */
    data object OpenPicker : CompressionEffect

    data class Message(val kind: MessageKind) : CompressionEffect

    data class Share(val id: String) : CompressionEffect

    enum class MessageKind {
        NOTHING_SELECTED,

        /** 用户选了图，但张张都读不出来（格式/权限/解码问题）。 */
        READ_FAILED,

        /** 已成功写入系统相册。 */
        SAVED,
        SHARE_FAILED,
        SAVE_FAILED,
        SAVE_UNSUPPORTED,
    }
}
