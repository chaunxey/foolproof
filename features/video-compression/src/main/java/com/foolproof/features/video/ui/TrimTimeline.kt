package com.foolproof.features.video.ui

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.VideoView
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.foolproof.features.video.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 裁剪时间轴：平台 `VideoView` 预览 + 抽帧缩略图条 + 双滑块。
 *
 * 刻意不引 ExoPlayer：只为播放本地文件不值得多 2–3MB（plan R6）。
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
) {
    if (durationMs <= 0) return

    val frames = rememberVideoFrames(videoId, frameCount = 8)
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    // 选中视频后不自动播放：由用户点【播放】才开始（避免一进页面就出声）
    var isPlaying by remember(videoId) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 固定高度 + 黑底：VideoView 自身按视频比例缩放并居中（letterbox），
                // 竖屏视频两侧留黑边，不会把整个页面撑高
                .height(PREVIEW_HEIGHT)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                // 点视频任意处切换播放/暂停（与常见播放器一致）
                .clickable {
                    val view = videoView
                    if (view != null) {
                        if (isPlaying) {
                            view.pause()
                            isPlaying = false
                        } else {
                            view.seekTo(startMs.toInt())
                            view.start()
                            isPlaying = true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(Uri.parse(videoId))
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            seekTo(startMs.toInt())
                        }
                        setOnCompletionListener { isPlaying = false }
                        videoView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // 播放中不显示任何按钮（跟常见播放器一致：播放时控件隐藏，点画面即暂停）；
            // 暂停/未开始时显示播放三角。点击由外层统一处理。
            if (!isPlaying) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play),
                        contentDescription = "播放",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }

        // 拖动滑块后回到起点并暂停，避免"播放位置与裁剪区间不一致"
        LaunchedEffect(startMs) {
            videoView?.let { view ->
                if (isPlaying) {
                    view.pause()
                    isPlaying = false
                }
                view.seekTo(startMs.toInt())
            }
        }

        if (frames.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(40.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                frames.forEach { frame ->
                    Image(
                        bitmap = frame,
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
                text = "片段 ${formatDuration(endMs - startMs)} / 总长 ${formatDuration(durationMs)}",
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

/**
 * 抽帧做时间轴缩略图：在 IO 线程逐帧取，拿到就释放 retriever（避免与转码抢解码器）。
 */
@Composable
private fun rememberVideoFrames(videoId: String, frameCount: Int): List<ImageBitmap> {
    val context = LocalContext.current
    var frames by remember(videoId) { mutableStateOf<List<ImageBitmap>>(emptyList()) }

    LaunchedEffect(videoId) {
        frames = withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(videoId))
                val duration = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                if (duration <= 0L) return@withContext emptyList()

                (0 until frameCount).mapNotNull { index ->
                    val timeUs = duration * 1000 * index / frameCount
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?.let { bitmap ->
                            runCatching { bitmap.asImageBitmap() }.getOrNull()
                        }
                }
            } catch (throwable: Throwable) {
                emptyList()
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    return frames
}

/** 预览区固定高度：避免竖屏视频把页面撑得过高。 */
private val PREVIEW_HEIGHT = 240.dp
