package com.foolproof.features.conversion.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.foolproof.base.log.LogUtil
import java.io.File

/** 存到系统下载目录（plan D7）：API ≥ 29 走 MediaStore，无需写权限（宪法 §8）。 */
class MediaStorePdfExporter(
    private val context: Context,
    private val log: LogUtil,
) {

    fun canSaveToDownloads(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun save(path: String): Boolean {
        if (!canSaveToDownloads()) return false
        val source = File(path)
        if (!source.exists()) return false

        return runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Foolproof")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values,
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            true
        }.onFailure { log.w { "save to downloads failed: ${it::class.simpleName}" } }
            .getOrDefault(false)
    }
}

/**
 * 启动清理 PDF 输出目录（spec FR-014）。
 *
 * 与视频模块同一模式：进程被杀时清理代码来不及跑，启动时目录里的一切都属于上个进程。
 */
class PdfOutputCleaner(
    private val outputDir: File,
    private val log: LogUtil,
) {
    fun cleanAll(): Int {
        if (!outputDir.exists()) return 0
        var deleted = 0
        runCatching {
            outputDir.listFiles()?.forEach { file ->
                if (file.isFile && file.delete()) {
                    deleted++
                    log.d { "startup cleanup: removed ${file.name}" }
                }
            }
        }
        return deleted
    }
}
