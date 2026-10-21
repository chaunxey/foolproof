package com.foolproof.features.conversion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.foolproof.base.log.LogUtil
import com.foolproof.features.conversion.domain.HeicUnsupportedException
import com.foolproof.features.conversion.domain.PdfImageLoader
import com.foolproof.features.conversion.domain.PdfPageSpec
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 图片加载：采样解码 + EXIF 方向修正（plan D6）。
 *
 * 采样是硬要求——20MP 原图直接解码约 80MB，多张就 OOM；A4 打印长边 2048 已足够。
 */
class BitmapImageLoader(
    private val context: Context,
    private val log: LogUtil,
) : PdfImageLoader {

    override suspend fun load(uri: String): Bitmap = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        if (isHeic(parsed) && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            // 宪法 §3.3：HEIC 在 API 24–27 必须明确提示并跳过，不得崩溃
            throw HeicUnsupportedException()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("invalid image bounds")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(
                bounds.outWidth,
                bounds.outHeight,
                PdfPageSpec.IMAGE_MAX_EDGE_PX,
            )
        }
        val decoded = context.contentResolver.openInputStream(parsed)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IOException("decode failed")

        applyExifRotation(parsed, decoded)
    }

    override suspend fun readSize(uri: String): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                bounds.outWidth to bounds.outHeight
            } else {
                null
            }
        }.getOrNull()
    }

    private fun isHeic(uri: Uri): Boolean {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: return false
        return mime.contains("heic", ignoreCase = true) || mime.contains("heif", ignoreCase = true)
    }

    private fun applyExifRotation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    companion object {
        /** 长边不超过 [maxEdge] 的最小 2 的幂次采样率。 */
        fun calculateInSampleSize(width: Int, height: Int, maxEdge: Int): Int {
            var sample = 1
            var longest = maxOf(width, height)
            while (longest / 2 >= maxEdge) {
                longest /= 2
                sample *= 2
            }
            return sample
        }
    }
}

/** 文本读取：先严格 UTF-8，失败回退 GBK（spec US2/AC6）。 */
class ContentUriTextLoader(
    private val context: Context,
    private val log: LogUtil,
) : com.foolproof.features.conversion.domain.PdfTextLoader {

    override suspend fun load(uri: String): String = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
            ?: throw IOException("cannot open text uri")

        decodeStrict(bytes, Charsets.UTF_8)
            ?: decodeStrict(bytes, charset("GBK"))
            ?: throw com.foolproof.features.conversion.domain.UnsupportedEncodingException()
    }

    private fun decodeStrict(bytes: ByteArray, charset: java.nio.charset.Charset): String? = runCatching {
        charset.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
            .decode(java.nio.ByteBuffer.wrap(bytes))
            .toString()
    }.getOrNull()
}
