package com.foolproof.features.compression.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
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
        ids.mapNotNull { id -> runCatching { readOne(id) }.getOrNull() }
    }

    private fun readOne(id: String): SourceImage {
        val uri = Uri.parse(id)
        val resolver = context.contentResolver

        var displayName = DEFAULT_NAME
        var sizeBytes = 0L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                }
            }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: throw IllegalStateException("无法打开输入流: $id")

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalStateException("无法读取尺寸，可能是系统不支持的格式: $displayName")
        }

        return SourceImage(
            id = id,
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = resolver.getType(uri),
            width = bounds.outWidth,
            height = bounds.outHeight,
        )
    }

    private companion object {
        const val DEFAULT_NAME = "image"
    }
}
