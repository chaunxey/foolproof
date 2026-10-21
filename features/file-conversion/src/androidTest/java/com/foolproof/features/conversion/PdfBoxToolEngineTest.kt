package com.foolproof.features.conversion

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.foolproof.base.log.LogUtil
import com.foolproof.features.conversion.data.PdfBoxToolEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfBoxToolEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun asset(name: String): File {
        val target = File(context.cacheDir, name)
        context.assets.open(name).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    @Test
    fun mergeSplitAndRotate() = runBlocking {
        val engine = PdfBoxToolEngine(context, LogUtil(false))
        val one = asset("pdf_one.pdf")
        val three = asset("pdf_three.pdf")

        val merged = engine.merge(
            listOf(Uri.fromFile(one).toString(), Uri.fromFile(three).toString()),
            File(context.cacheDir, "merged.pdf").absolutePath,
        )
        assertEquals(4, engine.pageCount(merged.path))

        val split = engine.split(
            Uri.fromFile(three).toString(),
            "2-3",
            File(context.cacheDir, "split.pdf").absolutePath,
        )
        assertEquals(2, engine.pageCount(split.path))

        val rotated = engine.rotate(
            Uri.fromFile(one).toString(),
            90,
            File(context.cacheDir, "rotated.pdf").absolutePath,
        )
        assertTrue(File(rotated.path).length() > 0)
        assertEquals(1, engine.pageCount(rotated.path))

        val rewritten = engine.rewrite(
            Uri.fromFile(three).toString(),
            listOf(3, 1),
            File(context.cacheDir, "rewritten.pdf").absolutePath,
        )
        assertEquals(2, engine.pageCount(rewritten.path))

        val compressed = engine.compress(
            Uri.fromFile(three).toString(),
            com.foolproof.features.conversion.domain.PdfCompressQuality.LOW,
            File(context.cacheDir, "compressed.pdf").absolutePath,
        )
        assertTrue(File(compressed.path).length() > 0)
        assertEquals(3, engine.pageCount(compressed.path))

        val imagesDir = File(context.cacheDir, "pdf_images_test").apply {
            deleteRecursively()
            mkdirs()
        }
        val images = engine.toImages(
            Uri.fromFile(three).toString(),
            imagesDir.absolutePath,
            com.foolproof.features.conversion.domain.PdfCompressQuality.LOW,
            "Foolproof_IMG_CONV_test",
        )
        assertEquals(3, images.size)
        assertTrue(images.all { File(it.path).length() > 0 })
    }
}
