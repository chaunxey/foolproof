package com.foolproof.features.video.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.domain.TrimRequest
import com.foolproof.features.video.domain.VideoFailure
import com.foolproof.features.video.domain.VideoProgress
import com.foolproof.features.video.domain.VideoTrimmer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Media3 时长裁剪。开启 trim optimization：关键帧对齐时可避免重编码，否则回退到正常转码。
 */
class Media3VideoTrimmer(
    private val context: Context,
    private val logUtil: LogUtil,
) : VideoTrimmer {

    private var activeTransformer: Transformer? = null

    override fun trim(request: TrimRequest): Flow<VideoProgress> = callbackFlow {
        if (request.endMs <= request.startMs) {
            trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
            close()
            return@callbackFlow
        }

        val outputFile = File(request.outputPath)
        outputFile.parentFile?.mkdirs()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(request.sourceId))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(request.startMs)
                    .setEndPositionMs(request.endMs)
                    .build(),
            )
            .build()
        val edited = EditedMediaItem.Builder(mediaItem).build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                val bytes = if (exportResult.fileSizeBytes > 0) {
                    exportResult.fileSizeBytes
                } else {
                    outputFile.length()
                }
                if (outputFile.exists()) {
                    trySend(VideoProgress.Success(outputFile.absolutePath, bytes))
                } else {
                    trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
                }
                close()
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exception: ExportException,
            ) {
                logUtil.w {
                    "trim failed: code=${exception.errorCode} ${exception::class.simpleName}"
                }
                trySend(VideoProgress.Failed(VideoFailure.TRANSCODE_FAILED))
                close()
            }
        }

        val transformer = Transformer.Builder(context)
            .experimentalSetTrimOptimizationEnabled(true)
            .addListener(listener)
            .build()
        activeTransformer = transformer
        transformer.start(edited, request.outputPath)

        val poller = launch {
            val holder = ProgressHolder()
            while (isActive) {
                delay(PROGRESS_POLL_INTERVAL_MS)
                val state = runCatching { transformer.getProgress(holder) }.getOrNull()
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    trySend(VideoProgress.Running(holder.progress))
                }
            }
        }

        awaitClose {
            poller.cancel()
            transformer.cancel()
            activeTransformer = null
        }
    }.flowOn(Dispatchers.Main.immediate)

    override suspend fun cancel() {
        activeTransformer?.cancel()
        activeTransformer = null
    }

    override suspend fun cleanupPartialFile(path: String) {
        repeat(CLEANUP_ATTEMPTS) { attempt ->
            val file = File(path)
            if (!file.exists()) return
            if (file.delete()) return
            if (attempt < CLEANUP_ATTEMPTS - 1) delay(CLEANUP_RETRY_DELAY_MS)
        }
    }

    private companion object {
        const val PROGRESS_POLL_INTERVAL_MS = 400L
        const val CLEANUP_ATTEMPTS = 10
        const val CLEANUP_RETRY_DELAY_MS = 100L
    }
}
