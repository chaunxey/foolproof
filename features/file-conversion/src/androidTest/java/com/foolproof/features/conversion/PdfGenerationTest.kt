package com.foolproof.features.conversion

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.foolproof.base.log.LogUtil
import com.foolproof.features.conversion.data.NativePdfGenerator
import com.foolproof.features.conversion.domain.PdfConvertRequest
import com.foolproof.features.conversion.domain.PdfImageLoader
import com.foolproof.features.conversion.domain.PdfPageSpec
import com.foolproof.features.conversion.domain.PdfProgress
import com.foolproof.features.conversion.domain.PdfSource
import com.foolproof.features.conversion.domain.PdfTextLoader
import java.io.File
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** T029：用 PdfRenderer 验证生成物（页数 + A4 尺寸），把 PdfDocument 链路真正跑一遍。 */
@RunWith(AndroidJUnit4::class)
class PdfGenerationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val imageLoader = object : PdfImageLoader {
        override suspend fun load(uri: String): Bitmap =
            Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF3366FF.toInt()) }

        override suspend fun readSize(uri: String) = 800 to 600
    }

    private val textLoader = object : PdfTextLoader {
        override suspend fun load(uri: String) = buildString {
            appendLine("# 标题一")
            appendLine()
            repeat(80) { appendLine("这是第 $it 行中文内容，用于验证折行与分页不会切断单行。") }
            appendLine("\n```\ncode block line\n```")
        }
    }

    private fun generator() = NativePdfGenerator(imageLoader, textLoader, LogUtil(enabled = false))

    @Test
    fun imagesProduceOneA4PageEach(): Unit = runBlocking {
        val output = File(context.cacheDir, "t029_images.pdf").apply { delete() }
        val request = PdfConvertRequest.FromImages(
            images = (1..3).map { PdfSource.Image("content://img$it", 800, 600) },
            outputPath = output.absolutePath,
        )
        val result = generator().convert(request).last()
        assertTrue("应成功：$result", result is PdfProgress.Success)
        assertEquals(3, (result as PdfProgress.Success).result.pageCount)

        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(3, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertEquals(PdfPageSpec.WIDTH_PT, page.width)
                    assertEquals(PdfPageSpec.HEIGHT_PT, page.height)
                }
            }
        }
        output.delete()
    }

    @Test
    fun longMarkdownProducesMultiplePages(): Unit = runBlocking {
        val output = File(context.cacheDir, "t029_text.pdf").apply { delete() }
        val request = PdfConvertRequest.FromText(
            text = PdfSource.Text("content://text", "note.md", 1024),
            outputPath = output.absolutePath,
        )
        val result = generator().convert(request).last()
        assertTrue("应成功：$result", result is PdfProgress.Success)
        val pages = (result as PdfProgress.Success).result.pageCount
        assertTrue("80 行中文应超过一页，实际 $pages", pages >= 2)

        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertEquals(pages, renderer.pageCount) }
        }
        output.delete()
    }
}
