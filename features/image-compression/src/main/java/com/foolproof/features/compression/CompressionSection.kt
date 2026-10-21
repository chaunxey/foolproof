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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import com.foolproof.base.ads.AdGateProvider
import com.foolproof.base.ads.FeatureId
import com.foolproof.base.ads.gateOrRun
import com.foolproof.base.util.ByteFormat
import com.foolproof.common.ui.FoolproofButton
import com.foolproof.common.ui.FoolproofSecondaryButton
import com.foolproof.base.util.findActivity
import com.foolproof.base.share.SharedContentStore
import com.foolproof.features.compression.domain.CompressionOutcome
import com.foolproof.features.compression.domain.FailureReason
import com.foolproof.features.compression.domain.OutcomeStatus
import com.foolproof.features.compression.domain.OutputFormat
import com.foolproof.features.compression.domain.SourceImage
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel

/**
 * 图片压缩功能的对外入口。
 *
 * 宪法 §3.2：这里只提供页面的一个 Section，整页由 `:app` 组装；本组件不持有 NavController。
 *
 * 多语言：所有用户可见文案都来自 `res/values/strings.xml`，本文件不写中文字面量
 * （格式名 JPEG / WebP 属于专有名词，保持原样）。
 */
@Composable
fun CompressionSection(
    modifier: Modifier = Modifier,
    viewModel: CompressionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val shared by SharedContentStore.content.collectAsState()
    var quickStart by remember { mutableStateOf(false) }
    LaunchedEffect(shared) {
        val payload = shared
        if (payload?.kind == SharedContentStore.Kind.IMAGE && !state.isRunning) {
            viewModel.dispatch(CompressionAction.ImagesPicked(payload.uris))
            SharedContentStore.consume()
            quickStart = true
        }
    }
    // 分享进来后直接开始压缩，省掉再点一次“开始压缩”
    LaunchedEffect(quickStart, state.images, state.isRunning) {
        if (quickStart && state.images.isNotEmpty() && !state.isRunning) {
            quickStart = false
            viewModel.dispatch(CompressionAction.Start)
        }
    }
    val context = LocalContext.current
    val activity = context.findActivity()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK_COUNT),
    ) { uris ->
        viewModel.dispatch(CompressionAction.ImagesPicked(uris.map { it.toString() }))
    }

    // 只记下事件类型，渲染时再取当前语言的文案（切换语言后不会残留旧语言文案）
    var message by remember { mutableStateOf<CompressionEffect.MessageKind?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                CompressionEffect.OpenPicker -> picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )

                is CompressionEffect.Message -> message = effect.kind

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

    // 一次批量压缩成功结束（至少一张有结果）记一次「成功使用」
    var wasRunning by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.isRunning, state.completedCount) {
        if (wasRunning && !state.isRunning && state.completedCount > 0) {
            AdGateProvider.gate?.onSuccess(FeatureId.IMAGE_COMPRESSION)
        }
        wasRunning = state.isRunning
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.compression_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            // 没有选图时重置没有意义，隐藏按钮（重置会清空选图、结果与预估）
            if (state.images.isNotEmpty()) {
                TextButton(onClick = { viewModel.dispatch(CompressionAction.Reset) }) {
                    Text(stringResource(R.string.compression_reset))
                }
            }
            FoolproofSecondaryButton(
                text = if (state.images.isEmpty()) {
                    stringResource(R.string.compression_pick_image)
                } else {
                    stringResource(R.string.compression_reselect)
                },
                onClick = { viewModel.dispatch(CompressionAction.SelectImages) },
                enabled = !state.isRunning,
            )
        }

        if (state.images.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_empty_images),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = stringResource(R.string.compression_empty_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        val maxTargetKb = state.maxTargetKb
        if (maxTargetKb != null && maxTargetKb >= 5) {
            TargetOptionRow(
                targetBytes = state.option.targetBytes,
                maxKb = maxTargetKb,
                enabled = !state.isRunning,
                onTargetChange = {
                    viewModel.dispatch(CompressionAction.ChangeTargetBytes(it))
                },
            )
        }

        OptionRow(
            format = state.option.format,
            quality = state.option.quality,
            enabled = !state.isRunning,
            estimate = when {
                state.estimateLoading -> stringResource(R.string.compression_estimating)
                state.estimateBytes != null -> stringResource(
                    R.string.compression_estimate,
                    ByteFormat.formatBytes(state.estimateBytes!!),
                )
                else -> null
            },
            onFormatChange = { viewModel.dispatch(CompressionAction.ChangeFormat(it)) },
            onQualityChange = { viewModel.dispatch(CompressionAction.ChangeQuality(it)) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val primary = stringResource(
                if (state.isRunning) R.string.compression_cancel else R.string.compression_start,
            )
            FoolproofButton(
                text = primary,
                onClick = {
                    if (state.isRunning) {
                        viewModel.dispatch(CompressionAction.Cancel)
                    } else {
                        // 每成功压缩 5 次，下一次开始前先看插屏
                        AdGateProvider.gate.gateOrRun(activity, FeatureId.IMAGE_COMPRESSION) {
                            viewModel.dispatch(CompressionAction.Start)
                        }
                    }
                },
                enabled = state.isRunning || state.canStart,
            )
            if (state.images.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.dispatch(CompressionAction.Clear) },
                    enabled = !state.isRunning,
                ) {
                    Text(stringResource(R.string.compression_clear))
                }
            }
        }

        if (state.images.isNotEmpty()) {
            val savedBytes = state.totalSavedBytes
            Text(
                text = if (savedBytes > 0) {
                    stringResource(
                        R.string.compression_progress_saved,
                        state.images.size,
                        state.completedCount,
                        ByteFormat.formatBytes(savedBytes),
                    )
                } else {
                    stringResource(
                        R.string.compression_progress,
                        state.images.size,
                        state.completedCount,
                    )
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        message?.let { kind ->
            Text(
                text = stringResource(kind.labelRes()),
                color = if (kind.isError()) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.images.forEach { image ->
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
private fun TargetOptionRow(
    targetBytes: Long?,
    maxKb: Int,
    enabled: Boolean,
    onTargetChange: (Long?) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        FilterChip(
            selected = targetBytes == null,
            onClick = { onTargetChange(null) },
            enabled = enabled,
            label = { Text(stringResource(R.string.compression_target_auto)) },
        )
        FilterChip(
            selected = targetBytes != null,
            onClick = { showDialog = true },
            enabled = enabled,
            label = {
                Text(
                    if (targetBytes == null) {
                        stringResource(R.string.compression_target_custom)
                    } else {
                        stringResource(
                            R.string.compression_target_kb,
                            (targetBytes / 1024L).toInt(),
                        )
                    },
                )
            },
        )
    }

    if (targetBytes != null) {
        Text(
            text = stringResource(R.string.compression_target_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (showDialog) {
        TargetKbDialog(
            initialKb = ((targetBytes ?: DEFAULT_TARGET_KB.toLong()) / 1024L).toInt(),
            maxKb = maxKb,
            onDismiss = { showDialog = false },
            onConfirm = {
                onTargetChange(it * 1024L)
                showDialog = false
            },
        )
    }
}

@Composable
private fun TargetKbDialog(
    initialKb: Int,
    maxKb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initialKb.coerceIn(MIN_TARGET_KB, maxKb).toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in MIN_TARGET_KB..maxKb

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.compression_target_dialog_title)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        if (input.length <= 5 && input.all { it.isDigit() }) text = input
                    },
                    label = { Text(stringResource(R.string.compression_target_input)) },
                    suffix = { Text("KB") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                )
                Text(
                    text = stringResource(R.string.compression_target_range, maxKb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.compression_target_confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.compression_clear))
            }
        },
    )
}

private const val DEFAULT_TARGET_KB = 200
private const val MIN_TARGET_KB = 5
private const val MAX_TARGET_KB = 10240

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
            Text(
                text = stringResource(R.string.compression_quality, quality),
                style = MaterialTheme.typography.bodyMedium,
            )
            estimate?.let {
                Text(
                    text = stringResource(R.string.compression_estimate_separator, it),
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
                    text = statusText(outcome),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor(outcome),
                )
            }
            when {
                outcome.canRetry -> TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.compression_retry))
                }
                outcome.outputPath != null -> Column {
                    TextButton(onClick = onShare) {
                        Text(stringResource(R.string.compression_share))
                    }
                    if (saveSupported) {
                        TextButton(onClick = onSave) {
                            Text(stringResource(R.string.compression_save_to_gallery))
                        }
                    }
                }
            }
        }
    }
}

/** 体积对比（原始 → 压缩后）：箭头与顺序也可能随语言变化，所以整体走格式串。 */
@Composable
private fun sizeSummary(image: SourceImage, outcome: CompressionOutcome): String {
    val before = ByteFormat.formatBytes(image.sizeBytes)
    val after = outcome.compressedBytes ?: return before
    return stringResource(R.string.compression_size_compare, before, ByteFormat.formatBytes(after))
}

/** 单张结果的状态文案；失败时再拼上可翻译的原因。 */
@Composable
private fun statusText(outcome: CompressionOutcome): String = when (outcome.status) {
    OutcomeStatus.PENDING -> stringResource(R.string.compression_status_pending)
    OutcomeStatus.RUNNING -> stringResource(R.string.compression_status_running)
    OutcomeStatus.SUCCESS -> stringResource(
        R.string.compression_status_success,
        outcome.savedPercent ?: 0,
    )
    OutcomeStatus.NO_GAIN -> stringResource(R.string.compression_status_no_gain)
    OutcomeStatus.FAILED -> stringResource(
        R.string.compression_status_failed,
        stringResource((outcome.failure ?: FailureReason.UNKNOWN).labelRes()),
    )
    OutcomeStatus.UNSUPPORTED -> stringResource(R.string.compression_status_unsupported)
}

/** 失败原因 → 文案资源；领域层只给类别，文案留在 UI 层。 */
private fun FailureReason.labelRes(): Int = when (this) {
    FailureReason.DECODE -> R.string.compression_failure_decode
    FailureReason.IO -> R.string.compression_failure_io
    FailureReason.UNSUPPORTED_FORMAT -> R.string.compression_failure_format
    FailureReason.UNKNOWN -> R.string.compression_failure_unknown
}

@Composable
private fun statusColor(outcome: CompressionOutcome) = when (outcome.status) {
    OutcomeStatus.FAILED, OutcomeStatus.UNSUPPORTED -> MaterialTheme.colorScheme.error
    OutcomeStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** 一次性提示 → 文案资源。 */
private fun CompressionEffect.MessageKind.labelRes(): Int = when (this) {
    CompressionEffect.MessageKind.NOTHING_SELECTED -> R.string.compression_msg_nothing_selected
    CompressionEffect.MessageKind.READ_FAILED -> R.string.compression_msg_read_failed
    CompressionEffect.MessageKind.SAVED -> R.string.compression_msg_saved
    CompressionEffect.MessageKind.SHARE_FAILED -> R.string.compression_msg_share_failed
    CompressionEffect.MessageKind.SAVE_FAILED -> R.string.compression_msg_save_failed
    CompressionEffect.MessageKind.SAVE_UNSUPPORTED -> R.string.compression_msg_save_unsupported
}

/** 成功类提示用正常文字色，失败类用错误色。 */
private fun CompressionEffect.MessageKind.isError(): Boolean =
    this != CompressionEffect.MessageKind.SAVED

private const val MAX_PICK_COUNT = 50
