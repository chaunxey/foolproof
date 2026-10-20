package com.foolproof.features.compression.domain

/** 单张图片的处理结果状态。 */
enum class OutcomeStatus {
    /** 已选中，尚未开始。 */
    PENDING,

    /** 正在压缩。 */
    RUNNING,

    /** 压缩成功且体积变小。 */
    SUCCESS,

    /** 压缩完成但体积没有变小（小图 / 高质量），默认保留原图。 */
    NO_GAIN,

    /** 处理失败，可重试。 */
    FAILED,

    /** 系统不支持该格式（如 API ≤ 27 上的 HEIC）。 */
    UNSUPPORTED,
}

/** 失败原因分类：只保留可展示的类别，不暴露底层异常细节。 */
enum class FailureReason {
    DECODE,
    IO,
    UNSUPPORTED_FORMAT,
    UNKNOWN,
}

/**
 * 单张图片的处理结果。
 *
 * @param compressedBytes 压缩成功（SUCCESS / NO_GAIN）时的体积，其余状态为 null
 * @param outputPath 结果文件路径，仅成功时有值
 */
data class CompressionOutcome(
    val id: String,
    val status: OutcomeStatus = OutcomeStatus.PENDING,
    val originalBytes: Long = 0L,
    val compressedBytes: Long? = null,
    val outputPath: String? = null,
    val failure: FailureReason? = null,
) {
    /** 节省百分比；无收益或未完成时返回 null。 */
    val savedPercent: Int?
        get() {
            val after = compressedBytes ?: return null
            if (originalBytes <= 0L) return null
            val saved = originalBytes - after
            return ((saved * 100) / originalBytes).toInt()
        }

    val canRetry: Boolean
        get() = status == OutcomeStatus.FAILED
}
