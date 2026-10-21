package com.foolproof.features.conversion

import com.foolproof.base.mvi.UiAction
import com.foolproof.base.mvi.UiEffect
import com.foolproof.base.mvi.UiState
import com.foolproof.features.conversion.domain.PdfFailure
import com.foolproof.features.conversion.domain.PdfResult
import com.foolproof.features.conversion.domain.PdfSource

/** 转换模式（spec：图片 / 文本，互斥）。 */
enum class ConversionMode { IMAGES, TEXT }

sealed interface ConversionPhase {
    data object Idle : ConversionPhase
    data object Ready : ConversionPhase

    data class Converting(val current: Int, val total: Int) : ConversionPhase
    data class Done(val result: PdfResult) : ConversionPhase
    data class Failed(val reason: PdfFailure) : ConversionPhase
}

data class FileConversionUiState(
    val mode: ConversionMode = ConversionMode.IMAGES,
    val images: List<PdfSource.Image> = emptyList(),
    val text: PdfSource.Text? = null,
    val phase: ConversionPhase = ConversionPhase.Idle,
    /** API ≥ 29 才显示【存到下载】（宪法 §8：低版本需要写权限）。 */
    val canSaveToDownloads: Boolean = false,
) : UiState {

    val isConverting: Boolean get() = phase is ConversionPhase.Converting

    val canConvert: Boolean
        get() = !isConverting && when (mode) {
            ConversionMode.IMAGES -> images.isNotEmpty()
            ConversionMode.TEXT -> text != null
        }

    val sourceCount: Int
        get() = if (mode == ConversionMode.IMAGES) images.size else if (text != null) 1 else 0
}

sealed interface ConversionAction : UiAction {
    data class ModeChanged(val mode: ConversionMode) : ConversionAction
    data class ImagesPicked(val uris: List<String>) : ConversionAction
    data class RemoveImage(val index: Int) : ConversionAction
    data class MoveImage(val from: Int, val to: Int) : ConversionAction
    data class TextPicked(val uri: String, val displayName: String, val bytes: Long) : ConversionAction
    data object Start : ConversionAction
    data object Cancel : ConversionAction
    data object Share : ConversionAction
    data object Open : ConversionAction
    data object SaveToDownloads : ConversionAction
}

sealed interface ConversionEffect : UiEffect {
    data class Message(val kind: MessageKind) : ConversionEffect
    data class Share(val path: String) : ConversionEffect
    data class Open(val path: String) : ConversionEffect

    enum class MessageKind {
        DONE,
        CANCELLED,
        NO_CONTENT,
        UNREADABLE,
        UNSUPPORTED_ENCODING,
        OUT_OF_SPACE,
        HEIC_UNSUPPORTED,
        GENERATION_FAILED,
        SAVED,
        SAVE_FAILED,
        SAVE_UNSUPPORTED,
        TOO_MANY_IMAGES,
        TEXT_TOO_LARGE,
    }
}
