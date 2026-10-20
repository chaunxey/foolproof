package com.foolproof.features.compression.platform

/**
 * 平台能力判定。`sdkInt` 通过构造参数传入，使两个 SDK 分支都能在 JVM 单测里覆盖。
 *
 * 这里刻意不返回 `android.graphics.Bitmap.CompressFormat`——否则领域层与单测都要依赖 Android 类型，
 * 映射交给数据层，本类只回答"能不能/用哪种"。
 */
class PlatformCapabilities(val sdkInt: Int) {

    /** HEIC 可靠解码（`ImageDecoder` 自 API 28 起）；宪法 §3.3 的版本门槛。 */
    val isHeicDecodable: Boolean
        get() = sdkInt >= API_P

    /** 能否直接把结果写进系统相册（Scoped Storage 免权限写自己的媒体，API 29+）。 */
    val canSaveToGallery: Boolean
        get() = sdkInt >= API_Q

    private companion object {
        const val API_P = 28
        const val API_Q = 29
    }
}
