package com.foolproof.features.conversion.domain

/** PDF 工具模式。 */
enum class PdfToolMode { MERGE, SPLIT, ROTATE, PAGES, COMPRESS }

/** 压缩档：JPEG 质量 + 渲染 DPI（越低越小）。 */
enum class PdfCompressQuality(val jpegQuality: Int, val dpi: Int) {
    HIGH(85, 200),
    MEDIUM(70, 150),
    LOW(50, 110),
}

data class PdfToolFile(
    val uri: String,
    val displayName: String,
    val pages: Int,
)

data class PdfToolResult(
    val path: String,
    val bytes: Long,
)

enum class PdfToolFailure {
    NO_FILES,
    NEED_TWO,
    INVALID_RANGE,
    UNREADABLE,
    FAILED,
}

interface PdfToolEngine {
    suspend fun pageCount(uri: String): Int?
    suspend fun merge(uris: List<String>, outputPath: String): PdfToolResult
    suspend fun split(uri: String, range: String, outputPath: String): PdfToolResult
    suspend fun rotate(uri: String, degrees: Int, outputPath: String): PdfToolResult

    /** 按给定页序（1-based）重写：用于页面重排/删除。 */
    suspend fun rewrite(uri: String, pages: List<Int>, outputPath: String): PdfToolResult

    /** 栅格化压缩：页面渲染成 JPEG 后重建 PDF（会丢失文字层）。 */
    suspend fun compress(
        uri: String,
        quality: PdfCompressQuality,
        outputPath: String,
    ): PdfToolResult
}

/**
 * 页码范围解析：`1-3,5` → [1,2,3,5]（1-based，去重、保持顺序）。
 * 非法/越界抛 [IllegalArgumentException]。
 */
object PdfPageRanges {

    fun parse(spec: String, pageCount: Int): List<Int> {
        require(pageCount > 0) { "empty document" }
        val result = LinkedHashSet<Int>()
        spec.split(',').forEach { raw ->
            val token = raw.trim()
            if (token.isEmpty()) return@forEach
            if ('-' in token) {
                val parts = token.split('-')
                require(parts.size == 2) { "bad range: $token" }
                val start = parts[0].trim().toIntOrNull()
                val end = parts[1].trim().toIntOrNull()
                require(start != null && end != null) { "bad range: $token" }
                require(start in 1..pageCount && end in start..pageCount) { "out of range: $token" }
                for (page in start..end) result += page
            } else {
                val page = token.toIntOrNull() ?: throw IllegalArgumentException("bad page: $token")
                require(page in 1..pageCount) { "out of range: $token" }
                result += page
            }
        }
        require(result.isNotEmpty()) { "empty range" }
        return result.toList()
    }
}
