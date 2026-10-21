package com.foolproof.features.video.domain

/** 分辨率档位：按**短边**定义（480p / 720p / 1080p）。 */
enum class ResolutionTier(val shortSide: Int, val label: String) {
    P480(480, "480p"),
    P720(720, "720p"),
    P1080(1080, "1080p"),
}

/** 码率档位。显示名由 UI 层翻译，领域层不持有文案。 */
enum class BitrateTier {
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * 压缩档位：分辨率档 + 码率档 → 具体编码参数。
 *
 * 纯函数，不触碰平台 API，可直接 JVM 单测（宪法质量门槛）。
 */
data class CompressionPreset(
    val resolution: ResolutionTier = ResolutionTier.P720,
    val bitrate: BitrateTier = BitrateTier.MEDIUM,
) {

    /** 目标视频码率（kbps）。表见 [BITRATE_TABLE]。 */
    fun videoBitrateKbps(): Int = BITRATE_TABLE.getValue(resolution).getValue(bitrate)

    /**
     * 目标短边像素：**不放大**——源比档位小时保持源尺寸；
     * 同时受编码器能力上限约束（例如软编 2048 上限对 1080p 也够，但别赌）。
     */
    fun targetShortSide(source: SourceVideo, encoderMaxSize: Int = Int.MAX_VALUE): Int {
        val capped = minOf(resolution.shortSide, source.shortSide, encoderMaxSize)
        return capped - (capped % 2) // H.264 要求宽高为偶数
    }

    /**
     * 预估输出体积（字节）。
     *
     * `(视频码率 + 音频码率) × 时长 ÷ 8`，音频按 [AUDIO_KBPS] 计；
     * 源无音轨时不计音频。
     */
    fun estimatedBytes(durationMs: Long, hasAudio: Boolean = true): Long {
        if (durationMs <= 0) return 0
        val audio = if (hasAudio) AUDIO_KBPS else 0
        val totalKbps = videoBitrateKbps() + audio
        return (totalKbps * 1000L / 8) * durationMs / 1000L
    }

    /** 码率显示值（Mbps，整数或一位小数），摘要文案由 UI 拼接。 */
    fun bitrateMbpsText(): String {
        val kbps = videoBitrateKbps()
        val mbps = kbps / 1000.0
        return if (mbps == mbps.toInt().toDouble()) {
            mbps.toInt().toString()
        } else {
            String.format("%.1f", mbps)
        }
    }

    companion object {
        const val AUDIO_KBPS = 128

        /** 码率表（kbps），H.264 / 30fps 经验值。 */
        val BITRATE_TABLE: Map<ResolutionTier, Map<BitrateTier, Int>> = mapOf(
            ResolutionTier.P480 to mapOf(
                BitrateTier.HIGH to 2_000,
                BitrateTier.MEDIUM to 1_200,
                BitrateTier.LOW to 700,
            ),
            ResolutionTier.P720 to mapOf(
                BitrateTier.HIGH to 4_000,
                BitrateTier.MEDIUM to 2_500,
                BitrateTier.LOW to 1_500,
            ),
            ResolutionTier.P1080 to mapOf(
                BitrateTier.HIGH to 8_000,
                BitrateTier.MEDIUM to 5_000,
                BitrateTier.LOW to 3_000,
            ),
        )

        /** 帧率上限：源更高时压到 30，源更低则保持源。 */
        const val MAX_FRAME_RATE = 30
    }
}
