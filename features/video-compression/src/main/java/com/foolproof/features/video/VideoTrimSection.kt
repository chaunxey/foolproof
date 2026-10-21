package com.foolproof.features.video

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import java.io.File
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.foolproof.base.util.ByteFormat
import com.foolproof.base.util.findActivity
import com.foolproof.common.ui.FoolproofButton
import com.foolproof.common.ui.FoolproofSecondaryButton
import com.foolproof.features.video.ui.ResultPreviewDialog
import com.foolproof.features.video.ui.TrimTimeline
import com.foolproof.features.video.ui.formatDuration
import org.koin.compose.viewmodel.koinViewModel

/** 视频时长裁剪：只保留选中的起止区间，不改分辨率/码率档位。 */
@Composable
fun VideoTrimSection(
    modifier: Modifier = Modifier,
    viewModel: VideoTrimViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    var messageRes by remember { mutableStateOf<Int?>(null) }
    var showResultDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.dispatch(VideoTrimAction.VideoPicked(uri.toString()))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VideoEffect.Message -> messageRes = effect.kind.labelRes()
                is VideoEffect.Share -> context.shareVideoFile(effect.path)
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.video_trim_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            FoolproofSecondaryButton(
                text = stringResource(
                    if (state.video == null) R.string.video_pick else R.string.video_repick,
                ),
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                enabled = !state.isTrimming,
            )
        }

        if (state.video == null) {
            Text(
                text = stringResource(R.string.video_trim_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
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

            TrimTimeline(
                videoId = video.id,
                durationMs = video.durationMs,
                startMs = state.trim.startMs,
                endMs = state.trim.endMs,
                onRangeChange = { start, end ->
                    viewModel.dispatch(VideoTrimAction.TrimChanged(start, end))
                },
                enabled = !state.isTrimming,
                videoAspect = video.width.toFloat() / video.height.toFloat(),
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = stringResource(
                    R.string.video_trim_keep,
                    formatDuration(state.trimDurationMs),
                    formatDuration(video.durationMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.isTrimming) {
            LinearProgressIndicator(
                progress = {
                    ((state.phase as? VideoPhase.Compressing)?.percent ?: 0) / 100f
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            FoolproofSecondaryButton(
                text = stringResource(R.string.video_cancel),
                onClick = { viewModel.dispatch(VideoTrimAction.Cancel) },
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            FoolproofButton(
                text = stringResource(
                    if (state.output == null) R.string.video_trim_start else R.string.video_trim_restart,
                ),
                onClick = { viewModel.dispatch(VideoTrimAction.Start) },
                enabled = state.canStart,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        messageRes?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.output?.let { output ->
            Text(
                text = stringResource(R.string.video_trim_result, ByteFormat.formatBytes(output.bytes)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FoolproofSecondaryButton(
                    text = stringResource(R.string.video_preview_result),
                    onClick = { showResultDialog = true },
                )
                FoolproofSecondaryButton(
                    text = stringResource(R.string.video_share),
                    onClick = { viewModel.dispatch(VideoTrimAction.Share) },
                )
                if (state.saveSupported) {
                    FoolproofSecondaryButton(
                        text = stringResource(R.string.video_save_to_gallery),
                        onClick = { viewModel.dispatch(VideoTrimAction.SaveToGallery) },
                    )
                }
            }
        }
    }

    val output = state.output
    val video = state.video
    if (showResultDialog && output != null && video != null) {
        ResultPreviewDialog(
            path = output.path,
            bytes = output.bytes,
            originalBytes = video.sizeBytes,
            durationMs = state.trimDurationMs,
            videoAspect = video.width.toFloat() / video.height.toFloat(),
            titleRes = R.string.video_trim_result_title,
            onDismiss = { showResultDialog = false },
        )
    }
}

private fun Context.shareVideoFile(path: String) {
    runCatching {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", File(path))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(
            Intent.createChooser(send, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}

private fun VideoEffect.MessageKind.labelRes(): Int = when (this) {
    VideoEffect.MessageKind.SOURCE_UNREADABLE -> R.string.video_msg_source_unreadable
    VideoEffect.MessageKind.NO_ENCODER -> R.string.video_msg_no_encoder
    VideoEffect.MessageKind.SOFTWARE_ENCODER_ONLY -> R.string.video_msg_software_encoder_only
    VideoEffect.MessageKind.OUT_OF_SPACE -> R.string.video_msg_out_of_space
    VideoEffect.MessageKind.TRANSCODE_FAILED -> R.string.video_msg_transcode_failed
    VideoEffect.MessageKind.CANCELLED -> R.string.video_msg_cancelled
    VideoEffect.MessageKind.DONE -> R.string.video_trim_done
    VideoEffect.MessageKind.SAVED -> R.string.video_msg_saved
    VideoEffect.MessageKind.SAVE_FAILED -> R.string.video_msg_save_failed
    VideoEffect.MessageKind.SAVE_UNSUPPORTED -> R.string.video_msg_save_unsupported
    VideoEffect.MessageKind.LONG_VIDEO -> R.string.video_msg_long_video
    VideoEffect.MessageKind.LARGER_THAN_SOURCE -> R.string.video_msg_larger_than_source
}
