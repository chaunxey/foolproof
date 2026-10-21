package com.foolproof.features.conversion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.foolproof.base.log.LogUtil
import com.foolproof.features.conversion.domain.PdfCompressQuality
import com.foolproof.features.conversion.domain.PdfPageRanges
import com.foolproof.features.conversion.domain.PdfToolEngine
import com.foolproof.features.conversion.domain.PdfToolResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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

    override suspend fun rewrite(
        uri: String,
        pages: List<Int>,
        outputPath: String,
    ): PdfToolResult = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "empty page order" }
        open(uri).use { source ->
            val output = PDDocument()
            try {
                pages.forEach { pageNumber ->
                    require(pageNumber in 1..source.numberOfPages) { "bad page $pageNumber" }
                    output.importPage(source.getPage(pageNumber - 1))
                }
                output.save(File(outputPath))
            } finally {
                output.close()
            }
        }
        PdfToolResult(outputPath, File(outputPath).length())
    }

    override suspend fun compress(
        uri: String,
        quality: PdfCompressQuality,
        outputPath: String,
    ): PdfToolResult = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "pdf_compress_in.pdf")
        copyToFile(uri, temp)
        val descriptor = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        val output = PDDocument()
        try {
            for (index in 0 until renderer.pageCount) {
                val page = renderer.openPage(index)
                try {
                    val scale = quality.dpi / 72f
                    val width = (page.width * scale).toInt().coerceIn(200, 2200)
                    val height = (page.height * scale).toInt().coerceIn(200, 2200)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val jpeg = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality.jpegQuality, jpeg)
                    bitmap.recycle()

                    val pdfPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                    output.addPage(pdfPage)
                    val image = JPEGFactory.createFromStream(
                        output,
                        ByteArrayInputStream(jpeg.toByteArray()),
                    )
                    PDPageContentStream(output, pdfPage).use { content ->
                        content.drawImage(
                            image,
                            0f,
                            0f,
                            page.width.toFloat(),
                            page.height.toFloat(),
                        )
                    }
                } finally {
                    page.close()
                }
            }
            output.save(File(outputPath))
        } finally {
            output.close()
            renderer.close()
            descriptor.close()
            temp.delete()
        }
        PdfToolResult(outputPath, File(outputPath).length())
    }

    private fun copyToFile(uri: String, target: File) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme == null) {
            File(uri).copyTo(target, overwrite = true)
        } else {
            context.contentResolver.openInputStream(parsed)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IllegalStateException("cannot open $uri")
        }
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
