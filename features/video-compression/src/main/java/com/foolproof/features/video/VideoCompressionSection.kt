package com.foolproof.features.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.foolproof.base.util.ByteFormat
import com.foolproof.features.video.ui.formatDuration
import com.foolproof.features.video.ui.PresetSelector
import com.foolproof.features.video.ui.TrimTimeline
import java.io.File
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * 视频压缩的对外入口（宪法 §3：只提供 Section，页面由 `:app` 组装为独立 tab）。
 *
 * 本组件自身**不滚动**（宪法 §3.2 v1.11 的"整页单一滚动容器"），滚动由 `:app` 的页面容器负责。
 */
@Composable
fun VideoCompressionSection(
    modifier: Modifier = Modifier,
    viewModel: VideoCompressionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var message by remember { mutableStateOf<UiMessage?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let { viewModel.dispatch(VideoAction.VideoPicked(it.toString())) }
    }

    // v1 只在前台压缩：离开页面（切 tab / 退出）即取消，避免后台长时间占用编码器（FR-015）
    DisposableEffect(Unit) {
        onDispose {
            if (viewModel.uiState.value.isCompressing) {
                viewModel.dispatch(VideoAction.Cancel)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VideoEffect.Message -> message = UiMessage(
                    text = effect.kind.label(),
                    isError = effect.kind.isError(),
                )

                is VideoEffect.Share -> context.shareVideo(effect.path)?.let { intent ->
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(3000)
            message = null
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("视频压缩", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                enabled = !state.isCompressing,
            ) {
                Text(if (state.video == null) "选择视频" else "重新选择")
            }
        }

        state.video?.let { video ->
            Text(
                text = "${video.displayName} · ${ByteFormat.formatBytes(video.sizeBytes)} · " +
                    "${video.width}x${video.height} · ${video.frameRate}fps",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            TrimTimeline(
                videoId = video.id,
                durationMs = video.durationMs,
                startMs = state.trim.startMs,
                endMs = state.trim.endMs,
                onRangeChange = { start, end ->
                    viewModel.dispatch(VideoAction.TrimChanged(start, end))
                },
                enabled = !state.isCompressing,
                modifier = Modifier.padding(top = 8.dp),
            )

            PresetSelector(
                preset = state.preset,
                estimatedBytes = state.estimatedBytes,
                trimDurationMs = state.trimDurationMs,
                frameRate = minOf(video.frameRate, com.foolproof.features.video.domain.CompressionPreset.MAX_FRAME_RATE),
                onResolutionChange = { viewModel.dispatch(VideoAction.ResolutionChanged(it)) },
                onBitrateChange = { viewModel.dispatch(VideoAction.BitrateChanged(it)) },
                enabled = !state.isCompressing,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.encoderNote?.let { note ->
            Text(
                text = note,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        message?.let {
            Text(
                text = it.text,
                color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when (val phase = state.phase) {
            is VideoPhase.Compressing -> {
                LinearProgressIndicator(
                    progress = { phase.percent / 100f },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Text(
                    text = "压缩中 ${phase.percent}% · 离开页面会取消",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = { viewModel.dispatch(VideoAction.Cancel) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("取消")
                }
            }

            else -> {
                Button(
                    onClick = { viewModel.dispatch(VideoAction.Start) },
                    enabled = state.canCompress,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(if (state.output == null) "开始压缩" else "重新压缩")
                }
            }
        }

        state.output?.let { output ->
            val original = state.video?.sizeBytes ?: 0L
            val duration = formatDuration(state.trimDurationMs)
            Text(
                text = buildString {
                    append("已完成：")
                    if (original > 0) append("${ByteFormat.formatBytes(original)} → ")
                    append(ByteFormat.formatBytes(output.bytes))
                    append(" · 时长 $duration")
                    if (state.savedBytes > 0) append("（节省 ${ByteFormat.formatBytes(state.savedBytes)}）")
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                OutlinedButton(onClick = { viewModel.dispatch(VideoAction.Share) }) {
                    Text("分享")
                }
                // API ≤ 28 需要写权限才能存相册，本应用不申请 → 不显示该入口（FR-013）
                if (state.saveSupported) {
                    OutlinedButton(onClick = { viewModel.dispatch(VideoAction.SaveToGallery) }) {
                        Text("存相册")
                    }
                }
            }
        }
    }
}

private data class UiMessage(val text: String, val isError: Boolean)

private fun VideoEffect.MessageKind.label(): String = when (this) {
    VideoEffect.MessageKind.SOURCE_UNREADABLE -> "无法读取该视频"
    VideoEffect.MessageKind.NO_ENCODER -> "当前设备没有可用的视频编码器"
    VideoEffect.MessageKind.SOFTWARE_ENCODER_ONLY -> "当前设备无硬件编码器，压缩会很慢"
    VideoEffect.MessageKind.OUT_OF_SPACE -> "存储空间不足"
    VideoEffect.MessageKind.TRANSCODE_FAILED -> "压缩失败，请重试"
    VideoEffect.MessageKind.CANCELLED -> "已取消"
    VideoEffect.MessageKind.DONE -> "压缩完成"
    VideoEffect.MessageKind.SAVED -> "已保存到相册（Movies/Foolproof）"
    VideoEffect.MessageKind.SAVE_FAILED -> "保存到相册失败"
    VideoEffect.MessageKind.SAVE_UNSUPPORTED -> "当前系统版本不支持直接保存到相册"
    VideoEffect.MessageKind.LONG_VIDEO -> "视频较长或较大，建议先裁剪片段再压缩"
}

private fun VideoEffect.MessageKind.isError(): Boolean = when (this) {
    VideoEffect.MessageKind.DONE,
    VideoEffect.MessageKind.SAVED,
    -> false

    else -> true
}

/** 用 FileProvider 拼分享 Intent；失败返回 null。 */
private fun Context.shareVideo(path: String): Intent? = runCatching {
    val file = File(path)
    val uri: Uri = FileProvider.getUriForFile(this, "$packageName.files", file)
    Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}.getOrNull()
