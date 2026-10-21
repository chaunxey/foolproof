package com.foolproof.features.video.data

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.domain.EncoderCapability
import com.foolproof.features.video.domain.EncoderCatalog

/**
 * 基于 `MediaCodecList` 的编码器能力查询。
 *
 * 实测（plan R2）：真机上同一 mime 往往**同时存在硬件与软件编码器**
 * （如 `c2.mtk.avc.encoder` hardware=true 与 `c2.android.avc.encoder` hardware=false），
 * 因此这里按「硬件优先 → 分辨率上限更大 → 帧率更高」排序，取最优。
 */
class MediaCodecEncoderCatalog(
    private val logUtil: LogUtil,
) : EncoderCatalog {

    private val codecInfos: List<MediaCodecInfo> by lazy {
        runCatching { MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.toList() }
            .onFailure { logUtil.w { "MediaCodecList failed: ${it::class.simpleName}" } }
            .getOrDefault(emptyList())
    }

    override fun findEncoder(mime: String): EncoderCapability? =
        codecInfos.asSequence()
            .filter { it.isEncoder && mime in it.supportedTypes }
            .mapNotNull { info -> capabilityOf(info, mime) }
            .sortedWith(
                compareByDescending<EncoderCapability> { it.isHardware }
                    .thenByDescending { it.maxShortSide }
                    .thenByDescending { it.maxFrameRate },
            )
            .firstOrNull()

    override fun availableVideoEncoders(): Set<String> {
        val result = mutableSetOf<String>()
        for (info in codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.startsWith("video/")) result += type
            }
        }
        return result
    }

    private fun capabilityOf(info: MediaCodecInfo, mime: String): EncoderCapability? {
        val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull() ?: return null
        val video = caps.videoCapabilities ?: return null

        val maxWidth = runCatching { video.supportedWidths.upper }.getOrNull() ?: return null
        val maxHeight = runCatching { video.supportedHeights.upper }.getOrNull() ?: return null
        val maxBitrate = runCatching { video.bitrateRange.upper / 1000 }.getOrElse { Int.MAX_VALUE / 1000 }
        val maxFps = runCatching { video.supportedFrameRates.upper.toDouble() }.getOrElse { 30.0 }

        return EncoderCapability(
            mime = mime,
            name = info.name,
            isHardware = info.isHardwareAccelerated,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxBitrateKbps = maxBitrate,
            maxFrameRate = maxFps,
        )
    }
}
