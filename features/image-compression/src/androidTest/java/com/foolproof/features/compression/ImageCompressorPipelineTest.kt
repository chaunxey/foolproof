package com.foolproof.features.compression

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foolproof.base.log.LogUtil
import com.foolproof.features.compression.data.AndroidImageCompressor
import com.foolproof.features.compression.domain.CompressionOption
import com.foolproof.features.compression.domain.OutputFormat
import com.foolproof.features.compression.domain.CompressionFailure
import com.foolproof.features.compression.domain.SourceImage
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 真机/模拟器上的压缩管线验证。覆盖 JVM 单测做不到的部分：
 * 1. 自研压编码管线的真实调用是否成功；
 * 2. `resolution()` 覆盖是否生效——**输出尺寸必须等于原尺寸**，而不是它默认的 612×816。
 */
@RunWith(AndroidJUnit4::class)
class ImageCompressorPipelineTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun imageCompressor() = AndroidImageCompressor(
        context = context,
        logUtil = LogUtil(enabled = false),
    )

    /** 生成一张带噪声的 PNG——单色图压缩后太小，测不出体积差异。 */
    private fun noisyPng(width: Int, height: Int, name: String): File {
        val pixels = IntArray(width * height)
        val random = Random(42)
        for (i in pixels.indices) {
            pixels[i] = 0xFF000000.toInt() or random.nextInt(0xFFFFFF)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)

        val file = File(context.cacheDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test
    fun compressProducesSmallerFileAndKeepsDimensions() {
        runBlocking {
            val source = noisyPng(width = 1200, height = 900, name = "pipeline_source.png")
            val image = SourceImage(
                id = Uri.fromFile(source).toString(),
                displayName = source.name,
                sizeBytes = source.length(),
                mimeType = "image/png",
                width = 1200,
                height = 900,
            )

            val result = imageCompressor().compress(image, CompressionOption(OutputFormat.JPEG, 80))

            val output = File(result.path)
            assertTrue("输出文件不存在: ${result.path}", output.exists())
            assertTrue(
                "压缩后应变小：${image.sizeBytes} -> ${result.bytes}",
                result.bytes < image.sizeBytes,
            )

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(result.path, bounds)
            assertTrue(
                "尺寸被意外缩小：期望 1200x900，实际 ${bounds.outWidth}x${bounds.outHeight}",
                bounds.outWidth == 1200 && bounds.outHeight == 900,
            )
            output.delete()
            source.delete()
        }
    }

    @Test
    fun webpOutputIsDecodable() {
        runBlocking {
            val source = noisyPng(width = 800, height = 600, name = "pipeline_webp.png")
            val image = SourceImage(
                id = Uri.fromFile(source).toString(),
                displayName = source.name,
                sizeBytes = source.length(),
                mimeType = "image/png",
                width = 800,
                height = 600,
            )

            val result = try {
                imageCompressor().compress(image, CompressionOption(OutputFormat.WEBP, 70))
            } catch (failure: CompressionFailure) {
                throw AssertionError("WebP 压缩失败，底层原因: ${failure.cause}", failure.cause)
            }

            assertTrue("WebP 输出应为 .webp: ${result.path}", result.path.endsWith(".webp"))
            val decoded = BitmapFactory.decodeFile(result.path)
            assertTrue("WebP 结果应可解码", decoded != null)
            decoded?.recycle()
            File(result.path).delete()
            source.delete()
        }
    }
}
