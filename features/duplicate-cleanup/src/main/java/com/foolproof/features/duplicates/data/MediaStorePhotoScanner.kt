package com.foolproof.features.duplicates.data

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.foolproof.base.log.LogUtil
import com.foolproof.features.duplicates.domain.PerceptualHash
import com.foolproof.features.duplicates.domain.PhotoItem
import com.foolproof.features.duplicates.domain.PhotoScanner
import com.foolproof.features.duplicates.domain.ScanEvent
import com.foolproof.features.duplicates.domain.ScanFailure
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield

/**
 * 基于 MediaStore 的相册扫描器。
 *
 * 逐张用 [ThumbnailPixelSource] 取采样像素并计算 dHash；单张失败只计入跳过数，不中断整批。
 */
class MediaStorePhotoScanner(
    private val context: Context,
    private val pixelSource: ThumbnailPixelSource,
    private val logUtil: LogUtil,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : PhotoScanner {

    override fun scan(): Flow<ScanEvent> = flow {
        val photos = try {
            queryPhotos()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            logUtil.w { "gallery query failed: ${throwable::class.simpleName}" }
            emit(ScanEvent.Failed(ScanFailure.QUERY_FAILED))
            return@flow
        }

        emit(ScanEvent.Started(photos.size))

        var scanned = 0
        var skipped = 0

        for (photo in photos) {
            if (photo.mimeType in HEIC_MIME_TYPES && sdkInt < API_P) {
                // 宪法 §3.3：API ≤ 27 不支持 HEIC，跳过而不是崩溃
                skipped++
                scanned++
                emit(ScanEvent.Progress(scanned, photos.size))
                continue
            }

            try {
                val pixels = pixelSource.sample(photo.uri.toString(), photo.width, photo.height)
                val item = PhotoItem(
                    id = photo.uri.toString(),
                    displayName = photo.displayName,
                    sizeBytes = photo.sizeBytes,
                    width = pixels.sourceWidth,
                    height = pixels.sourceHeight,
                    hash = PerceptualHash.dHash(pixels.gray),
                    lowInformation = PerceptualHash.isLowInformation(pixels.gray),
                    luma = pixels.gray.toList(),
                )
                emit(ScanEvent.Found(item))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                skipped++
                logUtil.d { "skip ${photo.displayName}: ${throwable::class.simpleName}" }
            }

            scanned++
            if (scanned % PROGRESS_EVERY == 0 || scanned == photos.size) {
                emit(ScanEvent.Progress(scanned, photos.size))
            }
            // 让取消能及时生效
            if (scanned % YIELD_EVERY == 0) yield()
        }

        emit(ScanEvent.Finished(scanned = scanned, skipped = skipped))
    }.flowOn(Dispatchers.IO)

    private fun queryPhotos(): List<RawPhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
        )

        val result = mutableListOf<RawPhoto>()
        val selection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            "${MediaStore.MediaColumns.IS_TRASHED}=0"
        } else {
            null
        }
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            "${MediaStore.Images.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

            while (cursor.moveToNext() && result.size < MAX_PHOTOS) {
                val id = cursor.getLong(idIndex)
                var width = cursor.getInt(widthIndex)
                var height = cursor.getInt(heightIndex)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                // 新保存的图有时宽高还没写进 MediaStore，回退解码一次，避免整张漏扫
                if (width <= 0 || height <= 0) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, bounds)
                        }
                    }
                    width = bounds.outWidth
                    height = bounds.outHeight
                }
                if (width <= 0 || height <= 0) continue

                result += RawPhoto(
                    uri = uri,
                    displayName = cursor.getString(nameIndex) ?: "image_$id",
                    sizeBytes = cursor.getLong(sizeIndex),
                    width = width,
                    height = height,
                    mimeType = cursor.getString(mimeIndex),
                )
            }
        }
        return result
    }

    private data class RawPhoto(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long,
        val width: Int,
        val height: Int,
        val mimeType: String?,
    )

    private companion object {
        const val API_P = 28
        const val MAX_PHOTOS = 20_000
        const val PROGRESS_EVERY = 8
        const val YIELD_EVERY = 16
        val HEIC_MIME_TYPES = setOf("image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence")
    }
}
