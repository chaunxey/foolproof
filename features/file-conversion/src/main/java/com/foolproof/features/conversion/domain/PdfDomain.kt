package com.foolproof.features.conversion.domain

/** 可转 PDF 的内容来源（宪法 §3.3：图片 / 文本两类）。 */
sealed interface PdfSource {

    data class Image(
        val uri: String,
        val width: Int,
        val height: Int,
        val displayName: String = "",
    ) : PdfSource

    data class Text(
        val uri: String,
        val displayName: String,
        val bytes: Long,
    ) : PdfSource
}

/** A4 竖版与各项上限（spec FR-009 / 边界情况）。长度单位均为 PDF 点（1/72 英寸）。 */
object PdfPageSpec {
    const val WIDTH_PT = 595
    const val HEIGHT_PT = 842
    const val IMAGE_MARGIN_PT = 24f
    const val TEXT_MARGIN_PT = 48f

    /** 单次最多 50 张图片。 */
    const val MAX_IMAGES = 50

    /** 文本文件上限 2MB。 */
    const val MAX_TEXT_BYTES = 2L * 1024 * 1024

    /** 图片长边上限：A4 打印足够，同时避免 OOM。 */
    const val IMAGE_MAX_EDGE_PX = 2048
}

/** 生成结果。 */
data class PdfResult(
    val path: String,
    val bytes: Long,
    val pageCount: Int,
)

/** 失败分类（spec FR-015）。 */
enum class PdfFailure {
    NO_CONTENT,
    UNREADABLE,
    UNSUPPORTED_ENCODING,
    OUT_OF_SPACE,
    HEIC_UNSUPPORTED,
    GENERATION_FAILED,
    UNKNOWN,
}

/** 转换进度。 */
sealed interface PdfProgress {
    data class Running(val current: Int, val total: Int) : PdfProgress
    data class Success(val result: PdfResult) : PdfProgress
    data class Failed(val reason: PdfFailure) : PdfProgress
    data object Cancelled : PdfProgress
}

/** 转换请求：两种模式互斥。 */
sealed interface PdfConvertRequest {
    val outputPath: String

    data class FromImages(
        val images: List<PdfSource.Image>,
        override val outputPath: String,
    ) : PdfConvertRequest

    data class FromText(
        val text: PdfSource.Text,
        override val outputPath: String,
    ) : PdfConvertRequest
}

/** PDF 生成器（抽象出来便于单测替换，宪法 §4）。 */
interface PdfConverter {
    fun convert(request: PdfConvertRequest): kotlinx.coroutines.flow.Flow<PdfProgress>
    suspend fun cancel()

    /** 删除半成品（spec FR-014）。 */
    suspend fun cleanupPartialFile(path: String)
}

/** 图片加载：Uri → 采样并纠正方向后的 Bitmap（抽象出来便于单测）。 */
interface PdfImageLoader {
    /** @throws HeicUnsupportedException 当系统不支持 HEIC（API < 28）。 */
    suspend fun load(uri: String): android.graphics.Bitmap

    /** 仅读取尺寸，不解码像素。 */
    suspend fun readSize(uri: String): Pair<Int, Int>?
}

/** 文本读取：Uri → 字符串（含编码判定）。 */
interface PdfTextLoader {
    /** @throws UnsupportedEncodingException 当 UTF-8 与 GBK 都解不开。 */
    suspend fun load(uri: String): String
}

class HeicUnsupportedException : Exception("HEIC not supported on this API level")

class UnsupportedEncodingException : Exception("cannot decode text")
/** 导出器接口（spec FR-019：生成/导出链路可替换，便于单测）。 */
interface PdfExporter {
    fun canSaveToDownloads(): Boolean
    fun save(path: String): Boolean
}