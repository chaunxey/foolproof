package com.foolproof.features.video.domain

/** 某个编码格式在本机的能力（实测得来，不写死）。 */
data class EncoderCapability(
    val mime: String,
    val name: String,
    val isHardware: Boolean,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrateKbps: Int,
    val maxFrameRate: Double,
) {
    /** 该编码器能编码的最大短边。 */
    val maxShortSide: Int
        get() = minOf(maxWidth, maxHeight)
}

/**
 * 编码器能力目录。抽成接口以便：
 * 1. ViewModel 能在 JVM 单测里用假实现；
 * 2. 未来换实现（如 Codec2 查询）不动上层。
 */
interface EncoderCatalog {

    /** 返回该 mime 下**最优**的编码器：硬件优先，其次分辨率上限更大者；没有则 null。 */
    fun findEncoder(mime: String): EncoderCapability?

    /** 本机可用的视频编码 mime 集合（用于决定 H.265 选项是否显示）。 */
    fun availableVideoEncoders(): Set<String>
}
