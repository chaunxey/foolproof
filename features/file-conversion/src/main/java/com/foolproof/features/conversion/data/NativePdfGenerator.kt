package com.foolproof.features.conversion.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.foolproof.base.log.LogUtil
import com.foolproof.features.conversion.domain.HeicUnsupportedException
import com.foolproof.features.conversion.domain.PdfConvertRequest
import com.foolproof.features.conversion.domain.PdfConverter
import com.foolproof.features.conversion.domain.PdfFailure
import com.foolproof.features.conversion.domain.PdfImageLoader
import com.foolproof.features.conversion.domain.PdfPageSpec
import com.foolproof.features.conversion.domain.PdfProgress
import com.foolproof.features.conversion.domain.PdfResult
import com.foolproof.features.conversion.domain.PdfTextLoader
import com.foolproof.features.conversion.domain.UnsupportedEncodingException
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * 原生 `PdfDocument` 生成器（plan D3，宪法 §2：不引第三方 PDF 库）。
 *
 * 图片页是位图；文本页用 `drawText` 画矢量文字，可被阅读器选中复制。
 */
class NativePdfGenerator(
    private val imageLoader: PdfImageLoader,
    private val textLoader: PdfTextLoader,
    private val log: LogUtil,
) : PdfConverter {

    @Volatile
    private var cancelled = false

    override fun convert(request: PdfConvertRequest): Flow<PdfProgress> = flow {
        cancelled = false
        val document = PdfDocument()
        val output = File(request.outputPath)
        output.parentFile?.mkdirs()

        try {
            val pageCount = when (request) {
                is PdfConvertRequest.FromImages -> renderImages(document, request)
                is PdfConvertRequest.FromText -> renderText(document, request)
            }
            if (pageCount == 0) {
                emit(PdfProgress.Failed(PdfFailure.NO_CONTENT))
                return@flow
            }
            withContext(Dispatchers.IO) { output.outputStream().use { document.writeTo(it) } }
            emit(PdfProgress.Success(PdfResult(output.absolutePath, output.length(), pageCount)))
        } catch (e: ConversionCancelled) {
            emit(PdfProgress.Cancelled)
        } catch (e: Exception) {
            log.w { "convert failed: ${e::class.simpleName}" }
            emit(PdfProgress.Failed(classify(e)))
        } finally {
            document.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun cancel() {
        cancelled = true
    }

    override suspend fun cleanupPartialFile(path: String) {
        repeat(3) {
            val file = File(path)
            if (!file.exists() || file.delete()) return
            delay(200)
        }
    }

    // region 图片模式

    private suspend fun FlowCollector<PdfProgress>.renderImages(
        document: PdfDocument,
        request: PdfConvertRequest.FromImages,
    ): Int {
        val perPage = request.perPage.coerceIn(1, 4)
        var page = 0
        request.images.chunked(perPage).forEachIndexed { pageIndex, group ->
            if (cancelled) throw ConversionCancelled()
            page = pageIndex + 1
            emit(PdfProgress.Running(page * group.size, request.images.size))

            val pageInfo = PdfDocument.PageInfo
                .Builder(PdfPageSpec.WIDTH_PT, PdfPageSpec.HEIGHT_PT, page)
                .create()
            val pdfPage = document.startPage(pageInfo)
            val cells = gridCells(perPage)
            group.forEachIndexed { index, image ->
                val bitmap = imageLoader.load(image.uri)
                try {
                    drawImageInCell(pdfPage.canvas, bitmap, cells[index])
                } finally {
                    bitmap.recycle()
                }
            }
            document.finishPage(pdfPage)
        }
        return page
    }

    /** 按每页张数计算各格子的绘制区域（含 6pt 间隔）。 */    private fun gridCells(perPage: Int): List<RectF> {
        val left = PdfPageSpec.IMAGE_MARGIN_PT
        val top = PdfPageSpec.IMAGE_MARGIN_PT
        val width = PdfPageSpec.WIDTH_PT - left * 2
        val height = PdfPageSpec.HEIGHT_PT - top * 2
        val gap = 6f
        return when (perPage) {
            1 -> listOf(RectF(left, top, left + width, top + height))
            2 -> {
                val cellHeight = (height - gap) / 2
                (0..1).map { i ->
                    val y = top + i * (cellHeight + gap)
                    RectF(left, y, left + width, y + cellHeight)
                }
            }
            else -> {
                val cellWidth = (width - gap) / 2
                val cellHeight = (height - gap) / 2
                (0..3).map { i ->
                    val col = i % 2
                    val row = i / 2
                    val x = left + col * (cellWidth + gap)
                    val y = top + row * (cellHeight + gap)
                    RectF(x, y, x + cellWidth, y + cellHeight)
                }
            }
        }
    }

    private fun drawImageInCell(canvas: Canvas, bitmap: android.graphics.Bitmap, cell: RectF) {
        val scale = minOf(cell.width() / bitmap.width, cell.height() / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = cell.left + (cell.width() - drawWidth) / 2f
        val top = cell.top + (cell.height() - drawHeight) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight), null)
    }

    @Suppress("unused")
    private fun drawImageCentered(canvas: Canvas, bitmap: android.graphics.Bitmap) {
        val availWidth = PdfPageSpec.WIDTH_PT - PdfPageSpec.IMAGE_MARGIN_PT * 2
        val availHeight = PdfPageSpec.HEIGHT_PT - PdfPageSpec.IMAGE_MARGIN_PT * 2
        val scale = minOf(availWidth / bitmap.width, availHeight / bitmap.height)
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = (PdfPageSpec.WIDTH_PT - drawWidth) / 2f
        val top = (PdfPageSpec.HEIGHT_PT - drawHeight) / 2f
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawWidth, top + drawHeight), null)
    }

    // endregion

    // region 文本模式

    private suspend fun FlowCollector<PdfProgress>.renderText(
        document: PdfDocument,
        request: PdfConvertRequest.FromText,
    ): Int {
        emit(PdfProgress.Running(1, 3))
        val raw = textLoader.load(request.text.uri)
        val blocks = MarkdownParser.parse(raw)
        if (blocks.isEmpty()) return 0

        val contentWidth = PdfPageSpec.WIDTH_PT - PdfPageSpec.TEXT_MARGIN_PT * 2
        val layout = TextLayout { text, style -> paintFor(style).measureText(text) }

        // 展开为「一行一条」并附带行高，分页时按行高累加，保证不切断单行
        data class Line(val text: String, val style: MdStyle, val height: Float)

        val lines = mutableListOf<Line>()
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    val size = headingSize(block.level)
                    layout.wrap(block.runs, contentWidth).forEach {
                        lines += Line(it.plainText, MdStyle(bold = true), size * 1.6f)
                    }
                }

                is MdBlock.Paragraph -> layout.wrap(block.runs, contentWidth).forEach {
                    lines += Line(it.plainText, MdStyle(), BODY_SIZE * 1.6f)
                }

                is MdBlock.ListBlock -> block.items.forEachIndexed { index, item ->
                    val prefix = if (block.ordered) "${index + 1}. " else "• "
                    layout.wrap(item, contentWidth - LIST_INDENT).forEach {
                        lines += Line(prefix + it.plainText, MdStyle(), BODY_SIZE * 1.6f)
                    }
                }

                is MdBlock.CodeBlock -> block.code.split('\n').forEach {
                    lines += Line(it, MdStyle(code = true), CODE_SIZE * 1.5f)
                }

                MdBlock.Divider -> lines += Line(DIVIDER, MdStyle(), BODY_SIZE * 1.2f)
            }
        }
        if (lines.isEmpty()) return 0

        emit(PdfProgress.Running(2, 3))

        var pageIndex = 0
        var y = PdfPageSpec.TEXT_MARGIN_PT + BODY_SIZE
        var page = startTextPage(document, ++pageIndex)

        lines.forEach { line ->
            if (cancelled) throw ConversionCancelled()
            if (y + line.height > PdfPageSpec.HEIGHT_PT - PdfPageSpec.TEXT_MARGIN_PT) {
                document.finishPage(page)
                page = startTextPage(document, ++pageIndex)
                y = PdfPageSpec.TEXT_MARGIN_PT + BODY_SIZE
            }
            page.canvas.drawText(line.text, PdfPageSpec.TEXT_MARGIN_PT, y, paintFor(line.style))
            y += line.height
        }
        document.finishPage(page)

        emit(PdfProgress.Running(3, 3))
        return pageIndex
    }

    private fun startTextPage(document: PdfDocument, index: Int): PdfDocument.Page =
        document.startPage(
            PdfDocument.PageInfo
                .Builder(PdfPageSpec.WIDTH_PT, PdfPageSpec.HEIGHT_PT, index)
                .create(),
        )

    private fun headingSize(level: Int): Float = when (level) {
        1 -> 22f
        2 -> 18f
        else -> 15f
    }

    private fun paintFor(style: MdStyle): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = if (style.code) CODE_SIZE else BODY_SIZE
        typeface = when {
            style.code -> Typeface.MONOSPACE
            style.bold -> Typeface.DEFAULT_BOLD
            style.italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            else -> Typeface.DEFAULT
        }
        color = if (style.code) Color.DKGRAY else Color.BLACK
    }

    // endregion

    private fun classify(error: Exception): PdfFailure = when (error) {
        is HeicUnsupportedException -> PdfFailure.HEIC_UNSUPPORTED
        is UnsupportedEncodingException -> PdfFailure.UNSUPPORTED_ENCODING
        is IOException -> PdfFailure.UNREADABLE
        else -> PdfFailure.GENERATION_FAILED
    }

    private class ConversionCancelled : Exception("cancelled")

    private companion object {
        const val BODY_SIZE = 14f
        const val CODE_SIZE = 12f
        const val LIST_INDENT = 14f
        const val DIVIDER = "────────────"
    }
}
