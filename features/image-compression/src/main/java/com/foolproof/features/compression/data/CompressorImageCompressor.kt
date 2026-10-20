package com.foolproof.features.compression.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.foolproof.base.log.LogUtil
import com.foolproof.features.compression.domain.CompressedFile
import com.foolproof.features.compression.domain.CompressionFailure
import com.foolproof.features.compression.domain.CompressionOption
import com.foolproof.features.compression.domain.FailureReason
import com.foolproof.features.compression.domain.ImageCompressor
import com.foolproof.features.compression.domain.OutputFormat
import com.foolproof.features.compression.domain.SourceImage
import com.foolproof.features.compression.platform.BitmapSampling
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.destination
import id.zelory.compressor.constraint.format
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 基于 Compressor（zetbaitsu）的唯一压缩实现（宪法 §2）。
 *
 * 两个必须知道的细节：
 * 1. Compressor 3.x 的 API 是 `Compressor.compress(context, file) { ... }`（suspend），输入必须是 `File`，
 *    所以从 content Uri 取图时要先落到缓存文件；
 * 2. **它的 DefaultConstraint 默认会把图缩到 612×816、质量 80**。因此必须显式调用 `resolution()`
 *    覆盖，否则"压缩"会变成"缩图"，与需求相反。
 */
class CompressorImageCompressor(
    private val context: Context,
    private val logUtil: LogUtil,
) : ImageCompressor {

    override suspend fun compress(image: SourceImage, option: CompressionOption): CompressedFile {
        val input = copyToCache(image, option)
        return try {
            withContext(Dispatchers.IO) {
                val outputDir = File(context.filesDir, OUTPUT_DIR).apply { mkdirs() }
                val target = ExportNaming.uniqueFile(outputDir, image.displayName, option)
                val (maxWidth, maxHeight) = targetDimensions(image)

                val result = Compressor.compress(context, input, Dispatchers.IO) {
                    quality(option.quality)
                    format(option.toCompressFormat())
                    resolution(maxWidth, maxHeight)
                    // destination 必须最后设置：约束按顺序链式生效，放在前面会被后续约束绕过
                    destination(target)
                }
                // 保险：若底层把结果写到了别处（旧版在扩展名变化时会自算落点），搬回我们的目标路径
                if (result.absolutePath != target.absolutePath) {
                    result.copyTo(target, overwrite = true)
                }
                CompressedFile(path = target.absolutePath, bytes = target.length())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: CompressionFailure) {
            throw failure
        } catch (throwable: Throwable) {
            logUtil.w { "compress failed (${throwable::class.simpleName}): ${image.displayName}" }
            throw CompressionFailure(FailureReason.DECODE, throwable)
        } finally {
            input.delete()
        }
    }

    /** 只有超过采样阈值时才降尺寸；否则传原尺寸，阻止 DefaultConstraint 擅自缩图。 */
    private fun targetDimensions(image: SourceImage): Pair<Int, Int> {
        val sample = BitmapSampling.calculateInSampleSize(image.width, image.height)
        if (sample <= 1) return image.width to image.height
        val width = (image.width / sample).coerceAtLeast(1)
        val height = (image.height / sample).coerceAtLeast(1)
        logUtil.d { "downscale ${image.width}x${image.height} -> ${width}x${height} (sample=$sample)" }
        return width to height
    }

    /**
     * Compressor 3.0.1 只认识 `JPEG` / `PNG` / `WEBP` 三个常量；传 API 30 才有的 `WEBP_LOSSY`
     * 会在它内部抛异常（真机测试已验证）。因此 WebP 一律用 `WEBP`，平台默认即有损。
     */
    private fun CompressionOption.toCompressFormat(): Bitmap.CompressFormat = when (format) {
        OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
        @Suppress("DEPRECATION")
        OutputFormat.WEBP -> Bitmap.CompressFormat.WEBP
    }

    private fun copyToCache(image: SourceImage, option: CompressionOption): File {
        // 输入临时文件带上目标扩展名：Compressor 3.0.1 在「输入无扩展名 + 输出扩展名变化」时
        // 会自行推算落点（落到不可写的目录），带扩展名可让它就地覆盖
        val target = File(context.cacheDir, "source_${System.nanoTime()}.${option.extension}")
        return try {
            val input = context.contentResolver.openInputStream(Uri.parse(image.id))
                ?: throw IllegalStateException("无法打开输入流")
            input.use { source ->
                target.outputStream().use { sink -> source.copyTo(sink) }
            }
            target
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            target.delete()
            throw CompressionFailure(FailureReason.IO, throwable)
        }
    }

    private companion object {
        const val OUTPUT_DIR = "compressed"
    }
}
