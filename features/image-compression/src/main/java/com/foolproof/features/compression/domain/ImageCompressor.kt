package com.foolproof.features.compression.domain

/**
 * 压缩能力的抽象。实现必须使用 Compressor（宪法 §2），这个接口只用于：
 * 1. 让 ViewModel 可在 JVM 单测里替换为假实现；
 * 2. 让异常边界清晰（实现内部转换失败为可识别的原因）。
 */
interface ImageCompressor {

    /**
     * 压缩单张图片。
     *
     * @return 压缩后的字节数；文件已写到私有目录
     * @throws CompressionFailure 解码 / IO 失败
     */
    suspend fun compress(image: SourceImage, option: CompressionOption): CompressedFile
}

data class CompressedFile(
    val path: String,
    val bytes: Long,
)

/** 压缩失败的领域异常，避免把底层异常类型泄漏到 ViewModel。 */
class CompressionFailure(
    val reason: FailureReason,
    cause: Throwable? = null,
) : Exception(reason.name, cause)
