package com.foolproof.features.video.ui

import android.content.Context
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foolproof.features.video.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 裁剪时间轴：预览播放器 + 抽帧缩略图条 + 双滑块。
 *
 * 播放器本身在 [VideoPreview] 里（压缩结果预览也复用它），这里只负责裁剪相关控件。
 */
@Composable
internal fun TrimTimeline(
    videoId: String,
    durationMs: Long,
    startMs: Long,
    endMs: Long,
    onRangeChange: (Long, Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    videoAspect: Float = 16f / 9f,
) {
    if (durationMs <= 0) return

    val context = LocalContext.current
    val frames = rememberVideoFrames(context, videoId, frameCount = 8)

    Column(modifier = modifier.fillMaxWidth()) {
        VideoPreview(
            uri = videoId,
            startMs = startMs,
            endMs = endMs,
            videoAspect = videoAspect,
            enabled = enabled,
        )

        if (frames.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                frames.forEach { frame ->
                    Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            }
        }

        RangeSlider(
            value = (startMs.toFloat() / durationMs)..(endMs.toFloat() / durationMs),
            onValueChange = { range ->
                onRangeChange(
                    (range.start * durationMs).toLong(),
                    (range.endInclusive * durationMs).toLong(),
                )
            },
            valueRange = 0f..1f,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatDuration(startMs),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                text = stringResource(
                    R.string.video_trim_range,
                    formatDuration(endMs - startMs),
                    formatDuration(durationMs),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(endMs),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** 抽 8–12 帧缩略图（plan R6）；失败时返回空列表，界面自动省略。 */
@Composable
private fun rememberVideoFrames(context: Context, videoId: String, frameCount: Int): List<Bitmap> {
    var frames by remember(videoId, frameCount) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(videoId, frameCount) {
        frames = withContext(Dispatchers.IO) {
            runCatching { extractFrames(context, videoId, frameCount) }.getOrDefault(emptyList())
        }
    }
    return frames
}

private fun extractFrames(context: Context, videoId: String, frameCount: Int): List<Bitmap> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(videoId))
        val durationMs = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull() ?: return emptyList()

        (0 until frameCount).map { index ->
            val positionMs = durationMs * index / frameCount
            retriever.getFrameAtTime(
                positionMs * 1000,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: return emptyList()
        }
    } finally {
        retriever.release()
    }
}
