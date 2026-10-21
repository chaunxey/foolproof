package com.foolproof.features.compression.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import com.foolproof.base.log.LogUtil
import com.foolproof.features.compression.domain.ImageMetadataReader
import com.foolproof.features.compression.domain.SourceImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** 通过 `ContentResolver` 读取图片元数据。单张失败只跳过该张，不中断整批。 */
class ContentResolverImageMetadataReader(
    private val context: Context,
    private val logUtil: LogUtil,
) : ImageMetadataReader {

    override suspend fun read(ids: List<String>): List<SourceImage> = withContext(Dispatchers.IO) {
        ids.mapNotNull { id ->
            try {
                readOne(id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                // 不静默吞掉：读不出来的原因必须可诊断，否则界面只会显示"没有选择任何图片"
                logUtil.w {
                    "metadata read failed for ${id.takeLast(12)}: " +
                        "${throwable::class.simpleName}: ${throwable.message}"
                }
                null
            }
        }
    }

    private fun readOne(id: String): SourceImage {
        val uri = Uri.parse(id)
        val resolver = context.contentResolver

        var displayName = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: DEFAULT_NAME
        var sizeBytes = 0L

        // 某些来源（如 Photo Picker 的 picker:// 风格 Uri）不支持完整的 projection，
        // 逐列兜底：查询失败不致命，名字/大小都可以从别处补。
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        }.onFailure { logUtil.d { "query failed (${it::class.simpleName}), fallback to afd" } }

        if (sizeBytes <= 0L) {
            runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                    sizeBytes = descriptor.length.coerceAtLeast(0L)
                }
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = resolver.openInputStream(uri)
            ?: throw IllegalStateException("openInputStream returned null")
        opened.use { BitmapFactory.decodeStream(it, null, bounds) }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException(
                "decode bounds failed (mime=${resolver.getType(uri)})",
            )
        }

        val hasLocation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).latLong != null } ?: false
        }.getOrDefault(false)

        return SourceImage(
            id = id,
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = resolver.getType(uri),
            width = bounds.outWidth,
            height = bounds.outHeight,
            hasLocation = hasLocation,
        )
    }

    private companion object {
        const val DEFAULT_NAME = "image"
    }
}
