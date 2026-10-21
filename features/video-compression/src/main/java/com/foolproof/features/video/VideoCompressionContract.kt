package com.foolproof.features.video

import com.foolproof.base.mvi.UiAction
import com.foolproof.base.mvi.UiEffect
import com.foolproof.base.mvi.UiState
import com.foolproof.features.video.domain.BitrateTier
import com.foolproof.features.video.domain.CompressionPreset
import com.foolproof.features.video.domain.ResolutionTier
import com.foolproof.features.video.domain.SourceVideo
import com.foolproof.features.video.domain.TrimRange
import com.foolproof.features.video.domain.VideoFailure

/** 页面阶段。 */
sealed interface VideoPhase {
    data object Idle : VideoPhase
    data object Loading : VideoPhase
    data object Ready : VideoPhase
    data class Compressing(val percent: Int) : VideoPhase
    data object Done : VideoPhase
    data class Failed(val reason: VideoFailure) : VideoPhase
}

/** 压缩产物。 */
data class CompressedVideo(val path: String, val bytes: Long)

data class VideoUiState(
    val video: SourceVideo? = null,
    val trim: TrimRange = TrimRange(0L, 0L),
    val preset: CompressionPreset = CompressionPreset(),
    val phase: VideoPhase = VideoPhase.Idle,
    val output: CompressedVideo? = null,
    /** 编码器相关提示（如"无硬件编码器，会很慢"），null 表示无需提示。 */
    val encoderNote: String? = null,
    /** API ≥ 29 才显示【存相册】（宪法 §8：低版本需要写权限，本应用不申请）。 */
    val saveSupported: Boolean = false,
) : UiState {

    val isCompressing: Boolean
        get() = phase is VideoPhase.Compressing

    val trimDurationMs: Long
        get() = if (video == null) 0L else trim.durationMs

    /** 预估体积（按裁剪后的时长与源是否有音轨计算）。 */
    val estimatedBytes: Long
        get() = video?.let { preset.estimatedBytes(trim.durationMs, it.hasAudio) } ?: 0L

    val canCompress: Boolean
        get() {
            if (video == null) return false
            if (isCompressing) return false
            if (phase is VideoPhase.Loading) return false
            // 无编码器是硬性阻断；其余失败（如转码失败）允许重试
            val failed = phase as? VideoPhase.Failed
            return failed?.reason != VideoFailure.NO_ENCODER
        }

    /** 省了多少（负数表示变大）。 */
    val savedBytes: Long
        get() = output?.let { (video?.sizeBytes ?: 0L) - it.bytes } ?: 0L
}

sealed interface VideoAction : UiAction {

    data class VideoPicked(val id: String) : VideoAction

    data class TrimChanged(val startMs: Long, val endMs: Long) : VideoAction

    data class ResolutionChanged(val tier: ResolutionTier) : VideoAction

    data class BitrateChanged(val tier: BitrateTier) : VideoAction

    data object Start : VideoAction

    data object Cancel : VideoAction

    /** 分享压缩结果（UI 用 FileProvider 拼 Intent）。 */
    data object Share : VideoAction

    /** 存相册（仅 API ≥ 29）。 */
    data object SaveToGallery : VideoAction

    /** 重新开始（清掉上一次结果）。 */
    data object Reset : VideoAction
}

sealed interface VideoEffect : UiEffect {

    data class Message(val kind: MessageKind) : VideoEffect

    /** 把输出路径交给 UI 去拉起分享面板。 */
    data class Share(val path: String) : VideoEffect

    enum class MessageKind {
        SOURCE_UNREADABLE,
        NO_ENCODER,
        SOFTWARE_ENCODER_ONLY,
        OUT_OF_SPACE,
        TRANSCODE_FAILED,
        CANCELLED,
        DONE,
        SAVED,
        SAVE_FAILED,
        SAVE_UNSUPPORTED,

        /** 源视频过长/过大，建议先裁剪（仍允许继续）。 */
        LONG_VIDEO,
    }
}
