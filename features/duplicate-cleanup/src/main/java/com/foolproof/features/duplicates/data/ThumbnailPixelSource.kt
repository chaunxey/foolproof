package com.foolproof.features.duplicates.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.foolproof.base.util.BitmapSampling
import com.foolproof.features.duplicates.domain.PerceptualHash
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
 * 把一张图片采样成 9×8 灰度，用于计算 dHash。
 *
 * **为什么不用 `ContentResolver.loadThumbnail`**（v1.14 修正）：
 * 实测发现两份**字节完全相同**的图片经 `loadThumbnail` 得到的像素不一致
 * （系统缩略图缓存/生成路径不同），导致同一张图算出不同哈希、被分到不同组。
 * 改为 `BitmapFactory` + `inSampleSize` 的确定性解码后，相同文件必然得到相同哈希。
 *
 * 性能上仍可控：只解到 [SOURCE_MAX_DIMENSION] 以内，且 `inSampleSize` 是 2 的幂。
 */
class ThumbnailPixelSource(
    private val context: Context,
) {

    suspend fun sample(id: String, width: Int, height: Int): SampledPixels = withContext(Dispatchers.IO) {
        val uri = Uri.parse(id)
        val bitmap = decodeSampled(uri, width, height)
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

    private fun decodeSampled(uri: Uri, width: Int, height: Int): Bitmap {
        val sample = BitmapSampling.calculateInSampleSize(
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            maxDimension = SOURCE_MAX_DIMENSION,
        )
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("cannot open $uri")
        return stream.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IllegalStateException("decode failed for $uri")
    }

    private companion object {
        /** 计算哈希前把图解到这个尺寸以内即可（9×8 目标，留足余量）。 */
        const val SOURCE_MAX_DIMENSION = 256
    }
}
