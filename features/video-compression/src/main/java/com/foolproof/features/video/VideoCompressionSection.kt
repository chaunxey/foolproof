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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.foolproof.base.ads.AdGateProvider
import com.foolproof.base.ads.FeatureId
import com.foolproof.base.ads.gateOrRun
import com.foolproof.base.util.ByteFormat
import com.foolproof.common.ui.FoolproofButton
import com.foolproof.common.ui.FoolproofSecondaryButton
import com.foolproof.base.util.findActivity
import com.foolproof.features.video.domain.CompressionPreset
import com.foolproof.features.video.ui.PresetSelector
import com.foolproof.features.video.ui.ResultPreviewDialog
import com.foolproof.features.video.ui.TrimTimeline
import com.foolproof.features.video.ui.VideoPreview
import com.foolproof.features.video.ui.formatDuration
import java.io.File
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * 视频压缩的对外入口（宪法 §3：只提供 Section，页面由 `:app` 组装为独立 tab）。
 *
 * 本组件自身**不滚动**（宪法 §3.2 v1.11 的「整页单一滚动容器」），滚动由 `:app` 的页面容器负责。
 */
@Composable
fun VideoCompressionSection(
    modifier: Modifier = Modifier,
    viewModel: VideoCompressionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    // 每成功压缩完一个视频记一次
    LaunchedEffect(state.phase) {
        if (state.phase is VideoPhase.Done) {
            AdGateProvider.gate?.onSuccess(FeatureId.VIDEO_COMPRESSION)
        }
    }
    var message by remember { mutableStateOf<UiMessage?>(null) }
    // 压缩结果改为弹窗预览，主页面不再切换状态
    var showResultDialog by remember { mutableStateOf(false) }

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
                    textRes = effect.kind.labelRes(),
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
            Text(
                text = stringResource(R.string.video_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            FoolproofSecondaryButton(
                text = if (state.video == null) {
                    stringResource(R.string.video_pick)
                } else {
                    stringResource(R.string.video_repick)
                },
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                enabled = !state.isCompressing,
            )
        }

        if (state.video == null) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_empty_video),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = stringResource(R.string.video_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        state.video?.let { video ->
            Text(
                text = stringResource(
                    R.string.video_meta,
                    video.displayName,
                    ByteFormat.formatBytes(video.sizeBytes),
                    video.width,
                    video.height,
                    video.frameRate,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            val aspect = video.width.toFloat() / video.height.toFloat()

            TrimTimeline(
                videoId = video.id,
                durationMs = video.durationMs,
                startMs = state.trim.startMs,
                endMs = state.trim.endMs,
                onRangeChange = { start, end ->
                    viewModel.dispatch(VideoAction.TrimChanged(start, end))
                },
                enabled = !state.isCompressing,
                videoAspect = aspect,
                modifier = Modifier.padding(top = 8.dp),
            )

            PresetSelector(
                preset = state.preset,
                estimatedBytes = state.estimatedBytes,
                trimDurationMs = state.trimDurationMs,
                frameRate = minOf(video.frameRate, CompressionPreset.MAX_FRAME_RATE),
                onResolutionChange = { viewModel.dispatch(VideoAction.ResolutionChanged(it)) },
                onBitrateChange = { viewModel.dispatch(VideoAction.BitrateChanged(it)) },
                enabled = !state.isCompressing,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.encoderNote?.let { note ->
            Text(
                text = stringResource(note.labelRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        message?.let { uiMessage ->
            Text(
                text = stringResource(uiMessage.textRes),
                color = if (uiMessage.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
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
                    text = stringResource(R.string.video_compressing, phase.percent),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = { viewModel.dispatch(VideoAction.Cancel) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.video_cancel))
                }
            }

            else -> {
                FoolproofButton(
                    text = stringResource(
                        if (state.output == null) R.string.video_start else R.string.video_restart,
                    ),
                    onClick = {
                        AdGateProvider.gate.gateOrRun(activity, FeatureId.VIDEO_COMPRESSION) {
                            viewModel.dispatch(VideoAction.Start)
                        }
                    },
                    enabled = state.canCompress,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        state.output?.let { output ->
            val original = state.video?.sizeBytes ?: 0L
            val duration = formatDuration(state.trimDurationMs)
            val after = ByteFormat.formatBytes(output.bytes)
            Text(
                text = if (original <= 0) {
                    stringResource(R.string.video_result_only, after, duration)
                } else {
                    val before = ByteFormat.formatBytes(original)
                    when {
                        state.savedBytes > 0 -> stringResource(
                            R.string.video_result_source_saved,
                            before,
                            after,
                            duration,
                            ByteFormat.formatBytes(state.savedBytes),
                        )
                        output.bytes > original -> stringResource(
                            R.string.video_result_source_larger,
                            before,
                            after,
                            duration,
                            ByteFormat.formatBytes(output.bytes - original),
                        )
                        else -> stringResource(R.string.video_result_source, before, after, duration)
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                OutlinedButton(onClick = { showResultDialog = true }) {
                    Text(stringResource(R.string.video_preview_result))
                }
                OutlinedButton(onClick = { viewModel.dispatch(VideoAction.Share) }) {
                    Text(stringResource(R.string.video_share))
                }
                // API ≤ 28 需要写权限才能存相册，本应用不申请 → 不显示该入口（FR-013）
                if (state.saveSupported) {
                    OutlinedButton(onClick = { viewModel.dispatch(VideoAction.SaveToGallery) }) {
                        Text(stringResource(R.string.video_save_to_gallery))
                    }
                }
            }
        }
    }

    val output = state.output
    val currentVideo = state.video
    if (showResultDialog && output != null && currentVideo != null) {
        ResultPreviewDialog(
            path = output.path,
            bytes = output.bytes,
            originalBytes = currentVideo.sizeBytes,
            durationMs = state.trimDurationMs,
            videoAspect = currentVideo.width.toFloat() / currentVideo.height.toFloat(),
            onDismiss = { showResultDialog = false },
        )
    }
}

/** 提示只存文案资源 id，渲染时再取当前语言文案。 */
private data class UiMessage(val textRes: Int, val isError: Boolean)

/** 软编提示 → 文案资源。 */
private fun EncoderNote.labelRes(): Int = when (this) {
    EncoderNote.SOFTWARE_ONLY -> R.string.video_msg_software_encoder_only
}

/** 一次性提示 → 文案资源。 */
private fun VideoEffect.MessageKind.labelRes(): Int = when (this) {
    VideoEffect.MessageKind.SOURCE_UNREADABLE -> R.string.video_msg_source_unreadable
    VideoEffect.MessageKind.NO_ENCODER -> R.string.video_msg_no_encoder
    VideoEffect.MessageKind.SOFTWARE_ENCODER_ONLY -> R.string.video_msg_software_encoder_only
    VideoEffect.MessageKind.OUT_OF_SPACE -> R.string.video_msg_out_of_space
    VideoEffect.MessageKind.TRANSCODE_FAILED -> R.string.video_msg_transcode_failed
    VideoEffect.MessageKind.CANCELLED -> R.string.video_msg_cancelled
    VideoEffect.MessageKind.DONE -> R.string.video_msg_done
    VideoEffect.MessageKind.SAVED -> R.string.video_msg_saved
    VideoEffect.MessageKind.SAVE_FAILED -> R.string.video_msg_save_failed
    VideoEffect.MessageKind.SAVE_UNSUPPORTED -> R.string.video_msg_save_unsupported
    VideoEffect.MessageKind.LONG_VIDEO -> R.string.video_msg_long_video
    VideoEffect.MessageKind.LARGER_THAN_SOURCE -> R.string.video_msg_larger_than_source
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
