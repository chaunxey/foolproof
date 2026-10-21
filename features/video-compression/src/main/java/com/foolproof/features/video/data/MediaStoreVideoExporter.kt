package com.foolproof.features.video.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.domain.VideoExporter
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 存相册（宪法 §8）：API ≥ 29 走 MediaStore + `IS_PENDING`，**不申请任何写权限**。
 *
 * API ≤ 28 需要"写外部存储"权限才能写共享存储，与本项目红线冲突，
 * 因此低版本直接不支持存相册（只保留分享）。
 */
class MediaStoreVideoExporter(
    private val context: Context,
    private val logUtil: LogUtil,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : VideoExporter {

    override fun canSaveToGallery(): Boolean = sdkInt >= Build.VERSION_CODES.Q

    override suspend fun saveToGallery(inputPath: String, displayName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!canSaveToGallery()) return@withContext false

            val source = File(inputPath)
            if (!source.exists() || source.length() <= 0L) {
                logUtil.w { "save failed: output missing" }
                return@withContext false
            }

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, ensureMp4(displayName))
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_MOVIES}/$SUBFOLDER",
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val uri = runCatching {
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrNull() ?: run {
                logUtil.w { "save failed: insert returned null" }
                return@withContext false
            }

            val copied = runCatching {
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out) }
                } ?: throw IllegalStateException("openOutputStream returned null")
            }

            if (copied.isFailure) {
                logUtil.w { "save failed: ${copied.exceptionOrNull()?.let { it::class.simpleName }}" }
                runCatching { resolver.delete(uri, null, null) }
                return@withContext false
            }

            val published = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            val updated = runCatching { resolver.update(uri, published, null, null) }.getOrDefault(0)
            if (updated <= 0) {
                logUtil.w { "save failed: could not clear IS_PENDING" }
                runCatching { resolver.delete(uri, null, null) }
                return@withContext false
            }

            true
        }

    private fun ensureMp4(name: String): String =
        if (name.endsWith(".mp4", ignoreCase = true)) name else "$name.mp4"

    private companion object {
        const val SUBFOLDER = "Foolproof"
    }
}
