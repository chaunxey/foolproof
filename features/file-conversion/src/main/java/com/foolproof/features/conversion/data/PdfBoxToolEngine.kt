package com.foolproof.features.conversion.data

import android.content.Context
import android.net.Uri
import com.foolproof.base.log.LogUtil
import com.foolproof.features.conversion.domain.PdfPageRanges
import com.foolproof.features.conversion.domain.PdfToolEngine
import com.foolproof.features.conversion.domain.PdfToolResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 PDFBox-Android 的离线 PDF 工具：合并 / 按范围拆分 / 旋转。
 * 所有操作只在本地内存与 filesDir 内完成，不上传。
 */
class PdfBoxToolEngine(
    private val context: Context,
    private val log: LogUtil,
) : PdfToolEngine {

    override suspend fun pageCount(uri: String): Int? = withContext(Dispatchers.IO) {
        runCatching { open(uri).use { it.numberOfPages } }.getOrNull()
    }

    override suspend fun merge(
        uris: List<String>,
        outputPath: String,
    ): PdfToolResult = withContext(Dispatchers.IO) {
        val output = PDDocument()
        try {
            uris.forEach { uri ->
                open(uri).use { source ->
                    source.pages.forEach { page -> output.importPage(page) }
                }
            }
            output.save(File(outputPath))
        } finally {
            output.close()
        }
        PdfToolResult(outputPath, File(outputPath).length())
    }

    override suspend fun split(
        uri: String,
        range: String,
        outputPath: String,
    ): PdfToolResult = withContext(Dispatchers.IO) {
        open(uri).use { source ->
            val pages = PdfPageRanges.parse(range, source.numberOfPages)
            val output = PDDocument()
            try {
                pages.forEach { pageNumber ->
                    output.importPage(source.getPage(pageNumber - 1))
                }
                output.save(File(outputPath))
            } finally {
                output.close()
            }
        }
        PdfToolResult(outputPath, File(outputPath).length())
    }

    override suspend fun rotate(
        uri: String,
        degrees: Int,
        outputPath: String,
    ): PdfToolResult = withContext(Dispatchers.IO) {
        open(uri).use { document ->
            document.pages.forEach { page ->
                page.rotation = ((page.rotation + degrees) % 360 + 360) % 360
            }
            document.save(File(outputPath))
        }
        PdfToolResult(outputPath, File(outputPath).length())
    }

    private fun open(uri: String): PDDocument {
        val parsed = Uri.parse(uri)
        val bytes = if (parsed.scheme == null) {
            File(uri).readBytes()
        } else {
            context.contentResolver.openInputStream(parsed)?.use { it.readBytes() }
                ?: throw IllegalStateException("cannot open $uri")
        }
        return PDDocument.load(bytes).also {
            log.d { "opened pdf: ${bytes.size} bytes" }
        }
    }
}
