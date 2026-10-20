package com.foolproof.features.duplicates.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Size
import com.foolproof.features.duplicates.domain.PerceptualHash
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 采样结果：9×8 灰度像素 + 原图尺寸。 */
data class SampledPixels(
    val gray: IntArray,
    val sourceWidth: Int,
    val sourceHeight: Int,
) {
    // IntArray 用 data class 需要显式 equals/hashCode；这里只做传输容器，避免误用
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * 把一张图片采样成 9×8 灰度。
 *
 * - API ≥ 29：用 `ContentResolver.loadThumbnail`，走系统缩略图缓存，比完整解码快一个数量级；
 * - API ≤ 28：退回「采样解码 + 缩放」，复用 `:base` 的 [com.foolproof.base.util.BitmapSampling]。
 *
 * 失败（HEIC 不支持、文件损坏、权限不足）一律抛异常，由扫描器计为跳过。
 */
class ThumbnailPixelSource(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) {

    suspend fun sample(id: String, width: Int, height: Int): SampledPixels = withContext(Dispatchers.IO) {
        val uri = Uri.parse(id)
        val bitmap = if (sdkInt >= API_Q) loadThumbnail(uri) else decodeSampled(uri, width, height)
        try {
            val scaled = Bitmap.createScaledBitmap(bitmap, PerceptualHash.WIDTH, PerceptualHash.HEIGHT, true)
            try {
                val pixels = IntArray(PerceptualHash.WIDTH * PerceptualHash.HEIGHT)
                scaled.getPixels(pixels, 0, PerceptualHash.WIDTH, 0, 0, PerceptualHash.WIDTH, PerceptualHash.HEIGHT)
                SampledPixels(
                    gray = PerceptualHash.grayscale(pixels),
                    sourceWidth = width,
                    sourceHeight = height,
                )
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadThumbnail(uri: Uri): Bitmap =
        context.contentResolver.loadThumbnail(
            uri,
            Size(PerceptualHash.WIDTH * 4, PerceptualHash.HEIGHT * 4),
            null,
        ) ?: throw IllegalStateException("thumbnail is null for $uri")

    private fun decodeSampled(uri: Uri, width: Int, height: Int): Bitmap {
        val sample = com.foolproof.base.util.BitmapSampling.calculateInSampleSize(
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            maxDimension = HASH_SOURCE_MAX_DIMENSION,
        )
        val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("cannot open $uri")
        return stream.use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalStateException("decode failed for $uri")
    }

    private companion object {
        const val API_Q = 29
        const val HASH_SOURCE_MAX_DIMENSION = 256
    }
}
