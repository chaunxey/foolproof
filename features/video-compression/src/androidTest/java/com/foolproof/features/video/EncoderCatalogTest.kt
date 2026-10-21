package com.foolproof.features.video

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 编码器能力探测（T005/T007）。
 *
 * 为什么必须先测：视频压缩要求「必须硬编、不静默退软编」，而档位上限（能否 1080p）
 * 取决于设备的 H.264 编码器能力。这一步把真实能力打印出来并落到 plan 的 R2。
 */
@RunWith(AndroidJUnit4::class)
class EncoderCatalogTest {

    @Test
    fun listHardwareVideoEncoders() {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val report = StringBuilder("\n===== VIDEO ENCODERS =====")

        var avcCount = 0
        var hardwareAvcCount = 0

        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val mime = info.supportedTypes.firstOrNull { it == "video/avc" || it == "video/hevc" }
                ?: continue

            if (mime == "video/avc") avcCount++
            if (mime == "video/avc" && info.isHardwareAccelerated) hardwareAvcCount++

            val caps = runCatching {
                info.getCapabilitiesForType(mime).videoCapabilities
            }.getOrNull()

            val size = if (caps != null) {
                runCatching { "${caps.supportedWidths.upper} x ${caps.supportedHeights.upper}" }
                    .getOrElse { "?" }
            } else {
                "?"
            }

            val bitrate = caps?.let {
                runCatching { "${it.bitrateRange.lower / 1000}..${it.bitrateRange.upper / 1000} kbps" }
                    .getOrElse { "?" }
            } ?: "?"

            val fps = caps?.let {
                runCatching { it.supportedFrameRates.upper.toString() }.getOrElse { "?" }
            } ?: "?"

            report.append("\n").append(
                "$mime | ${info.name} | hardware=${info.isHardwareAccelerated} " +
                    "| size=$size | bitrate=$bitrate | maxFps=$fps",
            )
        }

        report.append("\nsummary: avcEncoders=$avcCount hardwareAvc=$hardwareAvcCount")
        Log.i(TAG, report.toString())

        // 不把「必须硬编」写成断言：模拟器只有软编，这里只断言"存在可用编码器"，
        // 硬件门槛由应用在运行时判定并在 UI 明示（plan R2 / D4）。
        assertTrue("设备上找不到任何 video/avc 编码器", avcCount > 0)
    }

    @Test
    fun printProfileLevelsForAvc() {
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val report = StringBuilder("\n===== AVC PROFILE/LEVEL =====")

        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            if ("video/avc" !in info.supportedTypes) continue
            val caps = runCatching { info.getCapabilitiesForType("video/avc") }.getOrNull() ?: continue
            val profiles = caps.profileLevels
                ?.map { profileName(it.profile) }
                ?.distinct()
                ?.joinToString()
            report.append("\n${info.name} hardware=${info.isHardwareAccelerated} profiles=$profiles")
        }

        Log.i(TAG, report.toString())
    }

    private fun profileName(profile: Int): String = when (profile) {
        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "Baseline"
        MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "Main"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "High"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "High10"
        else -> "profile#$profile"
    }

    private companion object {
        const val TAG = "EncoderCatalogTest"
    }
}
