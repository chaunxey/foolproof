package com.foolproof.features.conversion

import com.foolproof.base.mvi.UiAction
import com.foolproof.base.mvi.UiEffect
import com.foolproof.base.mvi.UiState
import com.foolproof.features.conversion.domain.PdfCompressQuality
import com.foolproof.features.conversion.domain.PdfToolFile
import com.foolproof.features.conversion.domain.PdfToolMode
import com.foolproof.features.conversion.domain.PdfToolResult

enum class PdfToolPhase { IDLE, WORKING, DONE, FAILED }

data class PdfToolsUiState(
    val mode: PdfToolMode = PdfToolMode.MERGE,
    val files: List<PdfToolFile> = emptyList(),
    val range: String = "",
    val rotateDegrees: Int = 90,
    val pageOrder: List<Int> = emptyList(),
    val quality: PdfCompressQuality = PdfCompressQuality.MEDIUM,
    val phase: PdfToolPhase = PdfToolPhase.IDLE,
    val output: PdfToolResult? = null,
    val canSaveToDownloads: Boolean = false,
) : UiState {

    val canStart: Boolean
        get() {
            if (phase == PdfToolPhase.WORKING) return false
            return when (mode) {
                PdfToolMode.MERGE -> files.size >= 2
                PdfToolMode.SPLIT -> files.size == 1 && range.isNotBlank()
                PdfToolMode.ROTATE -> files.size == 1
                PdfToolMode.PAGES -> files.size == 1 && pageOrder.isNotEmpty()
                PdfToolMode.COMPRESS -> files.size == 1
            }
        }
}

data class PdfPick(val uri: String, val displayName: String)

sealed interface PdfToolsAction : UiAction {
    data class ModeChanged(val mode: PdfToolMode) : PdfToolsAction
    data class PdfsPicked(val picks: List<PdfPick>) : PdfToolsAction
    data class Remove(val uri: String) : PdfToolsAction
    data class MoveUp(val uri: String) : PdfToolsAction
    data class RangeChanged(val range: String) : PdfToolsAction
    data class RotateChanged(val degrees: Int) : PdfToolsAction
    data class PageMove(val index: Int, val delta: Int) : PdfToolsAction
    data class PageRemove(val index: Int) : PdfToolsAction
    data class QualityChanged(val quality: PdfCompressQuality) : PdfToolsAction

    data object Start : PdfToolsAction
    data object Reset : PdfToolsAction
    data object Share : PdfToolsAction
    data object SaveToDownloads : PdfToolsAction
}

sealed interface PdfToolsEffect : UiEffect {
    data class Message(val kind: MessageKind) : PdfToolsEffect
    data class Share(val path: String) : PdfToolsEffect

    enum class MessageKind {
        NEED_ONE,
        NEED_TWO,
        NEED_PAGES,
        INVALID_RANGE,
        FAILED,
        DONE,
        SAVED,
        SAVE_FAILED,
    }
}
