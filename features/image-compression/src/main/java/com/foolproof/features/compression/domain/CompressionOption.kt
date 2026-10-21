package com.foolproof.features.compression.domain

/** 输出格式，仅允许 JPEG / WebP（宪法 §1.1）。 */
enum class OutputFormat {
    JPEG,
    WEBP,
}

/**
 * 压缩参数。质量超出 0–100 时自动收敛，避免把非法值传给底层。
 */
data class CompressionOption(
    val format: OutputFormat = OutputFormat.JPEG,
    val quality: Int = DEFAULT_QUALITY,
    /** 目标体积（字节）；null 表示按质量压缩。 */
    val targetBytes: Long? = null,
    /** true：输出移除 GPS/设备等 EXIF；false：尽量保留原图元数据。 */
    val stripMetadata: Boolean = true,
) {
    init {
        require(quality in MIN_QUALITY..MAX_QUALITY) {
            "quality 必须在 $MIN_QUALITY..$MAX_QUALITY，实际 $quality"
        }
        require(targetBytes == null || targetBytes > 0) { "targetBytes 必须为正数" }
    }

    fun withQuality(value: Int): CompressionOption = copy(quality = value.coerceIn(MIN_QUALITY, MAX_QUALITY))

    fun withFormat(value: OutputFormat): CompressionOption = copy(format = value)

    fun withTargetBytes(value: Long?): CompressionOption =
        copy(targetBytes = value?.coerceAtLeast(MIN_TARGET_BYTES))

    fun withStripMetadata(value: Boolean): CompressionOption = copy(stripMetadata = value)

    /** 输出文件扩展名（不含点）。 */
    val extension: String
        get() = when (format) {
            OutputFormat.JPEG -> "jpg"
            OutputFormat.WEBP -> "webp"
        }

    companion object {
        const val MIN_QUALITY = 0
        const val MAX_QUALITY = 100
        const val DEFAULT_QUALITY = 80
        const val MIN_TARGET_BYTES = 5L * 1024

        val Default = CompressionOption()
    }
}
