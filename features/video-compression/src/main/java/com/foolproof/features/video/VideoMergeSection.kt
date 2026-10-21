package com.foolproof.features.video

import android.content.Context
import android.content.Intent
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.foolproof.base.ads.AdGateProvider
import com.foolproof.base.ads.FeatureId
import com.foolproof.base.ads.gateOrRun
import com.foolproof.base.util.findActivity
import com.foolproof.features.video.domain.MergeClip
import com.foolproof.features.video.ui.formatDuration
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/** 视频拼接：多选、上下移、首尾相接。 */
@Composable
fun VideoMergeSection(
    modifier: Modifier = Modifier,
    viewModel: VideoMergeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    var messageRes by remember { mutableStateOf<Int?>(null) }
    var trimTargetId by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.dispatch(VideoMergeAction.VideosPicked(uris.map { it.toString() }))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VideoMergeEffect.Message -> messageRes = effect.kind.labelRes()
                is VideoMergeEffect.Share -> context.shareVideo(effect.path)
            }
        }
    }

    LaunchedEffect(messageRes) {
        if (messageRes != null) {
            delay(3000)
            messageRes = null
        }
    }

    // 一次成功拼接记一次「成功使用」（广告门控）
    LaunchedEffect(state.output) {
        if (state.output != null) {
            AdGateProvider.gate?.onSuccess(FeatureId.VIDEO_MERGE)
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.video_merge_title), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
                    )
                },
                enabled = !state.isMerging,
            ) {
                Text(stringResource(R.string.video_merge_add))
            }
        }

        if (state.clips.isEmpty()) {
            Text(
                text = stringResource(R.string.video_merge_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                text = stringResource(
                    R.string.video_merge_selected,
                    state.clips.size,
                    formatDuration(state.totalDurationMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            state.clips.forEachIndexed { index, clip ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}. ${clip.source.displayName}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatDuration(clip.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    TextButton(
                        onClick = { trimTargetId = clip.source.id },
                        enabled = !state.isMerging,
                    ) { Text(stringResource(R.string.video_merge_trim)) }
                    if (index > 0) {
                        TextButton(
                            onClick = { viewModel.dispatch(VideoMergeAction.MoveUp(clip.source.id)) },
                            enabled = !state.isMerging,
                        ) { Text(stringResource(R.string.video_merge_move_up)) }
                    }
                    TextButton(
                        onClick = { viewModel.dispatch(VideoMergeAction.Remove(clip.source.id)) },
                        enabled = !state.isMerging,
                    ) { Text(stringResource(R.string.video_merge_remove)) }
                }
            }
        }

        if (state.isMerging) {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Text(
                text = stringResource(R.string.video_merge_merging, state.progress),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = { viewModel.dispatch(VideoMergeAction.Cancel) },
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.video_cancel))
            }
        } else {
            Button(
                onClick = {
                    AdGateProvider.gate.gateOrRun(activity, FeatureId.VIDEO_MERGE) {
                        viewModel.dispatch(VideoMergeAction.Start)
                    }
                },
                enabled = state.canStart,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.video_merge_start))
            }
        }

        messageRes?.let { res ->
            Text(
                text = stringResource(res),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.output?.let {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                OutlinedButton(onClick = { viewModel.dispatch(VideoMergeAction.Share) }) {
                    Text(stringResource(R.string.video_share))
                }
                if (state.saveSupported) {
                    OutlinedButton(onClick = { viewModel.dispatch(VideoMergeAction.SaveToGallery) }) {
                        Text(stringResource(R.string.video_save_to_gallery))
                    }
                }
            }
        }
    }

    trimTargetId?.let { id ->
        state.clips.firstOrNull { it.source.id == id }?.let { clip ->
            TrimClipDialog(
                clip = clip,
                onDismiss = { trimTargetId = null },
                onConfirm = { start, end ->
                    viewModel.dispatch(VideoMergeAction.TrimChanged(id, start, end))
                    trimTargetId = null
                },
            )
        }
    }
}

/** 单段裁剪：RangeSlider 同时控制起止点，至少保留 500ms。 */
@Composable
private fun TrimClipDialog(
    clip: MergeClip,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit,
) {
    var start by remember(clip) { mutableFloatStateOf(clip.startMs.toFloat()) }
    var end by remember(clip) { mutableFloatStateOf(clip.endMs.toFloat()) }
    val total = clip.source.durationMs.toFloat()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_merge_trim_title)) },
        text = {
            Column {
                Text(
                    text = "${formatDuration(start.toLong())} – ${formatDuration(end.toLong())}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                RangeSlider(
                    value = start..end,
                    onValueChange = { range ->
                        start = range.start.coerceIn(0f, total - MIN_TRIM_MS.toFloat())
                        end = range.endInclusive.coerceIn(start + MIN_TRIM_MS.toFloat(), total)
                    },
                    valueRange = 0f..total,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(start.toLong(), end.toLong()) }) {
                Text(stringResource(R.string.video_merge_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.video_cancel)) }
        },
    )
}

private fun VideoMergeEffect.MessageKind.labelRes(): Int = when (this) {
    VideoMergeEffect.MessageKind.NEED_TWO -> R.string.video_merge_need_two
    VideoMergeEffect.MessageKind.TOO_MANY -> R.string.video_merge_too_many
    VideoMergeEffect.MessageKind.READ_FAILED -> R.string.video_msg_source_unreadable
    VideoMergeEffect.MessageKind.MERGE_FAILED -> R.string.video_merge_failed
    VideoMergeEffect.MessageKind.CANCELLED -> R.string.video_msg_cancelled
    VideoMergeEffect.MessageKind.DONE -> R.string.video_merge_done
    VideoMergeEffect.MessageKind.SAVED -> R.string.video_msg_saved
    VideoMergeEffect.MessageKind.SAVE_FAILED -> R.string.video_msg_save_failed
    VideoMergeEffect.MessageKind.SAVE_UNSUPPORTED -> R.string.video_msg_save_unsupported
}

private fun Context.shareVideo(path: String) {
    runCatching {
        val file = File(path)
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
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

private const val MAX_PICK = 10
private const val MIN_TRIM_MS = 500L
