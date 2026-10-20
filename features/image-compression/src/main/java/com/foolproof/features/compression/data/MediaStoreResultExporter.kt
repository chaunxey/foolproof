package com.foolproof.features.compression.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.foolproof.features.compression.domain.ResultExporter
import com.foolproof.features.compression.platform.PlatformCapabilities
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 结果导出实现。
 *
 * - 分享：`FileProvider` + `ACTION_SEND`，全版本可用（无需任何权限）。
 * - 保存相册：仅在 API ≥ 29 允许，使用 `MediaStore` 的 `IS_PENDING` 流程，
 *   全程**不申请** `WRITE_EXTERNAL_STORAGE`（宪法 §8）。
 */
class MediaStoreResultExporter(
    private val context: Context,
    private val caps: PlatformCapabilities,
) : ResultExporter {

    override fun canSaveToGallery(): Boolean = caps.canSaveToGallery

    override suspend fun share(filePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext false

        runCatching {
            val uri = FileProvider.getUriForFile(context, authority(), file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeTypeOf(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        }.getOrDefault(false)
    }

    override suspend fun saveToGallery(filePath: String): Boolean = withContext(Dispatchers.IO) {
        if (!canSaveToGallery()) return@withContext false

        val file = File(filePath)
        if (!file.exists()) return@withContext false

        runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeTypeOf(file))
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/Foolproof",
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val target = resolver.insert(collection, values) ?: return@runCatching false

            resolver.openOutputStream(target)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: return@runCatching false

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(target, values, null, null)
            true
        }.getOrDefault(false)
    }

    private fun authority(): String = "${context.packageName}$AUTHORITY_SUFFIX"

    private fun mimeTypeOf(file: File): String = when (file.extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        else -> "image/*"
    }

    private companion object {
        const val AUTHORITY_SUFFIX = ".imagecompression"
    }
}
