package com.foolproof.features.compression

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.foolproof.features.compression.data.MediaStoreResultExporter
import com.foolproof.features.compression.platform.PlatformCapabilities
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 验证「保存到相册」在真实系统上的行为：API ≥ 29 免权限写入 MediaStore。
 */
@RunWith(AndroidJUnit4::class)
class MediaStoreExporterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun sampleJpeg(name: String): File {
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val file = File(context.filesDir, name)
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        return file
    }

    @Test
    fun capabilityFollowsSdkLevel() {
        assertEquals(
            android.os.Build.VERSION.SDK_INT >= 29,
            MediaStoreResultExporter(context, PlatformCapabilities(android.os.Build.VERSION.SDK_INT))
                .canSaveToGallery(),
        )
    }

    @Test
    fun saveToGalleryWritesMediaStoreEntryWithoutPermissions() = runBlocking {
        if (android.os.Build.VERSION.SDK_INT < 29) return@runBlocking

        val exporter = MediaStoreResultExporter(
            context,
            PlatformCapabilities(android.os.Build.VERSION.SDK_INT),
        )
        val file = sampleJpeg("gallery_${System.currentTimeMillis()}.jpg")

        val saved = exporter.saveToGallery(file.absolutePath)

        assertTrue("API ≥ 29 应能免权限保存到相册", saved)

        // 清理：按名称查找并删除刚插入的条目，避免污染模拟器相册
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(file.name),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                if (idIndex >= 0) {
                    val uri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        cursor.getLong(idIndex).toString(),
                    )
                    resolver.delete(uri, null, null)
                }
            }
        }
        file.delete()
    }
}
