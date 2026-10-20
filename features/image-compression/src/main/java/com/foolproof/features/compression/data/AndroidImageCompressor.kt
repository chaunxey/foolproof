package com.foolproof.features.compression.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.foolproof.base.log.LogUtil
import com.foolproof.features.compression.domain.CompressedFile
import com.foolproof.features.compression.domain.CompressionFailure
import com.foolproof.features.compression.domain.CompressionOption
import com.foolproof.features.compression.domain.FailureReason
import com.foolproof.features.compression.domain.ImageCompressor
import com.foolproof.features.compression.domain.OutputFormat
import com.foolproof.features.compression.domain.SourceImage
import com.foolproof.base.util.BitmapSampling
import java.io.File
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片压缩实现：`BitmapFactory` 采样解码 + `Bitmap.compress` 编码。
 *
 * 为什么自己实现而不用第三方库（宪法 §2，v1.9）：
 * - Compressor 已停更（2021），且实测有两个隐藏行为需要额外规避；
 * - Luban 2 虽在维护，但它的 API 不接受质量/格式/尺寸参数（自动压到"朋友圈画质"），
 *   与本功能「用户可选质量与格式、保持原尺寸」的规格不符。
 *
 * 本实现只依赖 Android 平台 API + `androidx.exifinterface`（用于纠正拍照方向），
 * 无 native 库、无第三方压缩算法。
 */
class AndroidImageCompressor(
    private val context: Context,
    private val logUtil: LogUtil,
) : ImageCompressor {

    override suspend fun compress(image: SourceImage, option: CompressionOption): CompressedFile =
        withContext(Dispatchers.IO) {
            try {
                val outputDir = File(context.filesDir, OUTPUT_DIR).apply { mkdirs() }
                val target = ExportNaming.uniqueFile(outputDir, image.displayName, option)

                val decoded = decodeSampled(image)
                val oriented = applyOrientation(decoded, image.id)
                if (oriented !== decoded) decoded.recycle()

                try {
                    write(oriented, target, option)
                } finally {
                    oriented.recycle()
                }

                CompressedFile(path = target.absolutePath, bytes = target.length())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: CompressionFailure) {
                throw failure
            } catch (throwable: Throwable) {
                logUtil.w { "compress failed (${throwable::class.simpleName}): ${image.displayName}" }
                throw CompressionFailure(classify(throwable), throwable)
            }
        }

    // region 解码

    private fun decodeSampled(image: SourceImage): Bitmap {
        val (width, height) = boundsOf(image)
        val sample = BitmapSampling.calculateInSampleSize(width, height)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val bitmap = openStream(image.id)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw CompressionFailure(FailureReason.DECODE)

        // 采样是 2 的幂，可能仍然超过长边上限，这里再精确缩一次
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= BitmapSampling.DEFAULT_MAX_DIMENSION) return bitmap

        val scale = BitmapSampling.DEFAULT_MAX_DIMENSION.toFloat() / longest
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** 元数据缺失或无效时，用一次 bounds 解码兜底，避免全尺寸解码导致 OOM。 */
    private fun boundsOf(image: SourceImage): Pair<Int, Int> {
        if (image.width > 0 && image.height > 0) return image.width to image.height

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openStream(image.id)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            throw CompressionFailure(FailureReason.DECODE)
        }
        return options.outWidth to options.outHeight
    }

    // endregion

    // region 方向

    /**
     * 按 EXIF 方向把像素转正。**重编码后不写回任何 EXIF**（含 GPS），满足 FR-017。
     */
    private fun applyOrientation(bitmap: Bitmap, id: String): Bitmap {
        val matrix = Matrix()
        when (readOrientation(id)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun readOrientation(id: String): Int = try {
        openStream(id)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        logUtil.d { "read exif failed: ${throwable::class.simpleName}" }
        ExifInterface.ORIENTATION_NORMAL
    }

    // endregion

    // region 编码

    private fun write(bitmap: Bitmap, target: File, option: CompressionOption) {
        val written = target.outputStream().use { out ->
            bitmap.compress(option.toCompressFormat(), option.quality, out)
        }
        if (!written) {
            target.delete()
            throw CompressionFailure(FailureReason.DECODE)
        }
    }

    private fun CompressionOption.toCompressFormat(): Bitmap.CompressFormat = when (format) {
        OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
        OutputFormat.WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    // endregion

    private fun openStream(id: String) =
        context.contentResolver.openInputStream(Uri.parse(id))

    private fun classify(throwable: Throwable): FailureReason = when (throwable) {
        is IOException -> FailureReason.IO
        is OutOfMemoryError -> FailureReason.DECODE
        is SecurityException -> FailureReason.IO
        else -> FailureReason.DECODE
    }

    private companion object {
        const val OUTPUT_DIR = "compressed"
    }
}
