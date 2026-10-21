package com.foolproof.features.conversion.domain

/** PDF 工具模式。 */
enum class PdfToolMode { MERGE, SPLIT, ROTATE }

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
