package com.foolproof.features.video.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.domain.SourceVideo
import com.foolproof.features.video.domain.VideoReader
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 用 `MediaMetadataRetriever` 读视频元信息。
 *
 * **读完立即 release**：转码期间不能再占着解码器资源（会与 Transformer 抢硬件解码器）。
 */
class ContentResolverVideoReader(
    private val context: Context,
    private val logUtil: LogUtil,
) : VideoReader {

    override suspend fun read(id: String): SourceVideo = withContext(Dispatchers.IO) {
        val uri = Uri.parse(id)
        val resolver = context.contentResolver

        var displayName = uri.lastPathSegment ?: DEFAULT_NAME
        var sizeBytes = 0L
        runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }.onFailure { logUtil.d { "query failed: ${it::class.simpleName}" } }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                ?.toFloatOrNull()?.toInt()?.takeIf { it > 0 } ?: DEFAULT_FRAME_RATE
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"

            if (durationMs <= 0L || width <= 0 || height <= 0) {
                throw IllegalStateException("invalid metadata: d=$durationMs ${width}x$height")
            }

            // 旋转 90/270 时宽高要交换，否则档位按短边计算会错
            val swapped = rotation == 90 || rotation == 270
            SourceVideo(
                id = id,
                displayName = displayName,
                sizeBytes = sizeBytes,
                durationMs = durationMs,
                width = if (swapped) height else width,
                height = if (swapped) width else height,
                frameRate = frameRate,
                hasAudio = hasAudio,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val DEFAULT_NAME = "video"
        const val DEFAULT_FRAME_RATE = 30
    }
}
