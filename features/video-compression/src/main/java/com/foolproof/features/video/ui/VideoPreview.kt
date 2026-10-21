package com.foolproof.features.video.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.foolproof.features.video.R
import kotlinx.coroutines.delay

/**
 * 视频预览播放器（不含裁剪控件）。
 *
 * 之所以独立出来：压缩完成后要在**同一个位置**切换预览「原视频」与「压缩结果」，
 * 两者都需要"点画面播放/暂停 + 底部进度条 + 播到终点自动停"这套行为。
 *
 * @param uri 视频地址，可以是 content:// 或 file://
 * @param startMs 起始位置：进入即停在此处，播放也从这里开始
 * @param endMs 终点：播到这里自动暂停并回到 [startMs]
 * @param videoAspect 画面宽高比，用于决定预览框尺寸
 */
@Composable
internal fun VideoPreview(
    uri: String,
    startMs: Long,
    endMs: Long,
    videoAspect: Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    // 不自动播放：由用户点【播放】才开始（避免一进页面就出声）
    var isPlaying by remember(uri) { mutableStateOf(false) }
    var positionMs by remember(uri) { mutableStateOf(startMs) }
    // 回调在 factory 里注册，用 updatedState 保证拿到最新的区间
    val latestStartMs by rememberUpdatedState(startMs)
    val latestEndMs by rememberUpdatedState(endMs)

    // 播放时轮询位置（VideoView 没有位置回调）；暂停时保留当前位置，进度条不回跳
    LaunchedEffect(isPlaying, startMs, endMs, uri) {
        if (!isPlaying) return@LaunchedEffect
        while (true) {
            val view = videoView
            if (view != null) {
                val position = view.currentPosition.toLong()
                // 播到终点就停：暂停并回到起点，与"只看这一段"的语义一致
                if (position >= endMs) {
                    view.pause()
                    view.seekTo(startMs.toInt())
                    positionMs = startMs
                    isPlaying = false
                    break
                }
                positionMs = position
            }
            delay(100)
        }
    }

    // 被移除（关闭弹窗 / 切 tab）时停止播放，避免控件已消失但声音还在
    DisposableEffect(Unit) {
        onDispose { videoView?.stopPlayback() }
    }

    val fraction = playbackFraction(positionMs, startMs, endMs)
    val playLabel = stringResource(R.string.video_preview_play)
    val progressLabel = stringResource(R.string.video_preview_progress, (fraction * 100).toInt())

    // 让画面尽可能大，同时保证竖屏视频不把页面撑爆：
    // 以「最大可用高度」和「可用宽度」两个约束里更紧的那个决定尺寸，保持视频原始比例。
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val aspect = videoAspect.coerceIn(0.3f, 3f)
        val widthByHeight = PREVIEW_MAX_HEIGHT * aspect
        val previewWidth = minOf(widthByHeight, maxWidth)
        val previewHeight = previewWidth / aspect

        Box(
            modifier = Modifier
                .width(previewWidth)
                .height(previewHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black)
                // 点画面任意处切换播放/暂停（与常见播放器一致）
                .clickable(enabled = enabled) {
                    val view = videoView ?: return@clickable
                    if (isPlaying) {
                        view.pause()
                        isPlaying = false
                    } else {
                        // 位置已跑到区间外才回起点；否则从当前位置接着播
                        if (view.currentPosition !in startMs.toInt() until endMs.toInt()) {
                            view.seekTo(startMs.toInt())
                        }
                        view.start()
                        isPlaying = true
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(Uri.parse(uri))
                        setOnPreparedListener { player ->
                            player.isLooping = false
                            seekTo(latestStartMs.toInt())
                        }
                        setOnCompletionListener {
                            // 播到文件末尾（通常就是窗口终点之后）：停下来并回到起点
                            isPlaying = false
                            seekTo(latestStartMs.toInt())
                            positionMs = latestStartMs
                        }
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
                        contentDescription = playLabel,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            // 底部播放进度条：以「播放窗口」为 0..1
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.25f))
                    // 读屏用户也能知道播到哪了
                    .semantics { contentDescription = progressLabel },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(Color.White),
                )
            }
        }
    }
}

/** 预览区最大高度：分辨率再高也不超过它，避免竖屏视频把页面撑爆。 */
private val PREVIEW_MAX_HEIGHT = 400.dp

/**
 * 播放进度（0..1），以播放窗口为基准：起点为 0、终点为 1。
 *
 * 抽成纯函数便于单测——界面里只负责把 [positionMs] 喂进来。
 */
internal fun playbackFraction(positionMs: Long, startMs: Long, endMs: Long): Float {
    val span = (endMs - startMs).coerceAtLeast(1L)
    return ((positionMs - startMs).toFloat() / span).coerceIn(0f, 1f)
}
