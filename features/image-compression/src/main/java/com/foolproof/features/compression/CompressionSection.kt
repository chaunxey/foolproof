package com.foolproof.features.compression

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.foolproof.base.util.ByteFormat
import com.foolproof.features.compression.domain.CompressionOutcome
import com.foolproof.features.compression.domain.OutcomeStatus
import com.foolproof.features.compression.domain.OutputFormat
import com.foolproof.features.compression.domain.SourceImage
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel

/**
 * 图片压缩功能的对外入口。
 *
 * 宪法 §3.2：这里只提供页面的一个 Section，整页由 `:app` 组装；本组件不持有 NavController。
 */
@Composable
fun CompressionSection(
    modifier: Modifier = Modifier,
    viewModel: CompressionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK_COUNT),
    ) { uris ->
        viewModel.dispatch(CompressionAction.ImagesPicked(uris.map { it.toString() }))
    }

    var message by remember { mutableStateOf<UiMessage?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                CompressionEffect.OpenPicker -> picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )

                is CompressionEffect.Message -> message = UiMessage(
                    text = effect.kind.label(),
                    isError = effect.kind.isError(),
                )

                is CompressionEffect.Share -> Unit
            }
        }
    }

    // 提示自动消失，避免"卡在屏幕上"或被用户误认为状态
    androidx.compose.runtime.LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2500)
            message = null
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("图片压缩", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = { viewModel.dispatch(CompressionAction.SelectImages) },
                enabled = !state.isRunning,
            ) {
                Text(if (state.images.isEmpty()) "选择图片" else "重新选择")
            }
        }

        OptionRow(
            format = state.option.format,
            quality = state.option.quality,
            enabled = !state.isRunning,
            estimate = when {
                state.estimateLoading -> "预估中…"
                state.estimateBytes != null -> "预估 ${ByteFormat.formatBytes(state.estimateBytes!!)}"
                else -> null
            },
            onFormatChange = { viewModel.dispatch(CompressionAction.ChangeFormat(it)) },
            onQualityChange = { viewModel.dispatch(CompressionAction.ChangeQuality(it)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val primary = if (state.isRunning) "取消" else "开始压缩"
            Button(
                onClick = {
                    viewModel.dispatch(
                        if (state.isRunning) CompressionAction.Cancel else CompressionAction.Start,
                    )
                },
                enabled = state.isRunning || state.canStart,
            ) {
                Text(primary)
            }
            if (state.images.isNotEmpty()) {
                TextButton(onClick = { viewModel.dispatch(CompressionAction.Clear) }, enabled = !state.isRunning) {
                    Text("清空")
                }
            }
        }

        if (state.images.isNotEmpty()) {
            Text(
                text = "共 ${state.images.size} 张，已完成 ${state.completedCount} 张" +
                    if (state.totalSavedBytes > 0) "，节省 ${ByteFormat.formatBytes(state.totalSavedBytes)}" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        message?.let { uiMessage ->
            Text(
                text = uiMessage.text,
                color = if (uiMessage.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.images, key = { it.id }) { image ->
                ImageRow(
                    image = image,
                    outcome = state.outcomeOf(image.id),
                    saveSupported = state.saveSupported,
                    onRetry = { viewModel.dispatch(CompressionAction.Retry(image.id)) },
                    onShare = { viewModel.dispatch(CompressionAction.Share(image.id)) },
                    onSave = { viewModel.dispatch(CompressionAction.SaveToGallery(image.id)) },
                )
            }
        }
    }
}

@Composable
private fun OptionRow(
    format: OutputFormat,
    quality: Int,
    enabled: Boolean,
    estimate: String?,
    onFormatChange: (OutputFormat) -> Unit,
    onQualityChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = format == OutputFormat.JPEG,
                onClick = { onFormatChange(OutputFormat.JPEG) },
                enabled = enabled,
                label = { Text("JPEG") },
            )
            FilterChip(
                selected = format == OutputFormat.WEBP,
                onClick = { onFormatChange(OutputFormat.WEBP) },
                enabled = enabled,
                label = { Text("WebP") },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("质量 $quality", style = MaterialTheme.typography.bodyMedium)
            estimate?.let {
                Text(
                    text = "  ·  $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Slider(
            value = quality.toFloat(),
            onValueChange = { onQualityChange(it.roundToInt()) },
            valueRange = 0f..100f,
            enabled = enabled,
        )
    }
}

@Composable
private fun ImageRow(
    image: SourceImage,
    outcome: CompressionOutcome,
    saveSupported: Boolean,
    onRetry: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = image.id,
                contentDescription = image.displayName,
                modifier = Modifier.size(56.dp),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = image.displayName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = sizeSummary(image, outcome),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = outcome.status.label(outcome),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(outcome),
                )
            }
            when {
                outcome.canRetry -> TextButton(onClick = onRetry) { Text("重试") }
                outcome.outputPath != null -> Column {
                    TextButton(onClick = onShare) { Text("分享") }
                    if (saveSupported) {
                        TextButton(onClick = onSave) { Text("存相册") }
                    }
                }
            }
        }
    }
}

private fun sizeSummary(image: SourceImage, outcome: CompressionOutcome): String {
    val before = ByteFormat.formatBytes(image.sizeBytes)
    val after = outcome.compressedBytes ?: return before
    return "$before → ${ByteFormat.formatBytes(after)}"
}

private fun OutcomeStatus.label(outcome: CompressionOutcome): String = when (this) {
    OutcomeStatus.PENDING -> "等待中"
    OutcomeStatus.RUNNING -> "压缩中…"
    OutcomeStatus.SUCCESS -> "完成，节省 ${outcome.savedPercent ?: 0}%"
    OutcomeStatus.NO_GAIN -> "无收益（未变小）"
    OutcomeStatus.FAILED -> "失败：${outcome.failure?.label() ?: "未知"}"
    OutcomeStatus.UNSUPPORTED -> "系统不支持该格式"
}

private fun com.foolproof.features.compression.domain.FailureReason.label(): String = when (this) {
    com.foolproof.features.compression.domain.FailureReason.DECODE -> "解码失败"
    com.foolproof.features.compression.domain.FailureReason.IO -> "读写失败"
    com.foolproof.features.compression.domain.FailureReason.UNSUPPORTED_FORMAT -> "格式不支持"
    com.foolproof.features.compression.domain.FailureReason.UNKNOWN -> "未知错误"
}

@Composable
private fun statusColor(outcome: CompressionOutcome) = when (outcome.status) {
    OutcomeStatus.FAILED, OutcomeStatus.UNSUPPORTED -> MaterialTheme.colorScheme.error
    OutcomeStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun CompressionEffect.MessageKind.label(): String = when (this) {
    CompressionEffect.MessageKind.NOTHING_SELECTED -> "没有选择任何图片"
    CompressionEffect.MessageKind.READ_FAILED -> "所选图片无法读取（格式或权限问题）"
    CompressionEffect.MessageKind.SAVED -> "已保存到相册（Pictures/Foolproof）"
    CompressionEffect.MessageKind.SHARE_FAILED -> "分享失败"
    CompressionEffect.MessageKind.SAVE_FAILED -> "保存到相册失败"
    CompressionEffect.MessageKind.SAVE_UNSUPPORTED -> "当前系统版本不支持直接保存到相册"
}

/** 成功类提示用正常文字色，失败类用错误色。 */
private fun CompressionEffect.MessageKind.isError(): Boolean =
    this != CompressionEffect.MessageKind.SAVED

private data class UiMessage(val text: String, val isError: Boolean)


private const val MAX_PICK_COUNT = 50
