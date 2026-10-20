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
) {
    init {
        require(quality in MIN_QUALITY..MAX_QUALITY) {
            "quality 必须在 $MIN_QUALITY..$MAX_QUALITY，实际 $quality"
        }
    }

    fun withQuality(value: Int): CompressionOption = copy(quality = value.coerceIn(MIN_QUALITY, MAX_QUALITY))

    fun withFormat(value: OutputFormat): CompressionOption = copy(format = value)

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

        val Default = CompressionOption()
    }
}
