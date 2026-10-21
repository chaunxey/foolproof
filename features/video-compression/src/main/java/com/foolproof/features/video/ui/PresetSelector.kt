package com.foolproof.features.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foolproof.base.util.ByteFormat
import com.foolproof.features.video.R
import com.foolproof.features.video.domain.BitrateTier
import com.foolproof.features.video.domain.CompressionPreset
import com.foolproof.features.video.domain.ResolutionTier
import java.util.Locale

/**
 * 档位选择器：分辨率三档 + 码率三档，并显示目标参数与预估体积。
 *
 * 纯展示组件（参数由外部传入），便于以后复用与预览。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PresetSelector(
    preset: CompressionPreset,
    estimatedBytes: Long,
    trimDurationMs: Long,
    frameRate: Int,
    targetSizeMb: Int?,
    maxTargetMb: Int,
    onResolutionChange: (ResolutionTier) -> Unit,
    onBitrateChange: (BitrateTier) -> Unit,
    onTargetSizeChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(stringResource(R.string.video_resolution), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ResolutionTier.entries.forEach { tier ->
                FilterChip(
                    selected = preset.resolution == tier,
                    onClick = { onResolutionChange(tier) },
                    enabled = enabled,
                    label = { Text(tier.label) },
                )
            }
        }

        Text(
            stringResource(R.string.video_target_size),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        var showTargetDialog by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = targetSizeMb == null,
                onClick = { onTargetSizeChange(null) },
                enabled = enabled,
                label = { Text(stringResource(R.string.video_target_auto)) },
            )
            FilterChip(
                selected = targetSizeMb != null,
                onClick = { showTargetDialog = true },
                enabled = enabled,
                label = {
                    Text(
                        if (targetSizeMb != null) {
                            stringResource(R.string.video_target_chip, targetSizeMb)
                        } else {
                            stringResource(R.string.video_target_custom)
                        },
                    )
                },
            )
        }

        if (showTargetDialog) {
            TargetSizeDialog(
                initialMb = (targetSizeMb ?: DEFAULT_TARGET_MB).coerceAtMost(maxTargetMb),
                maxMb = maxTargetMb,
                onDismiss = { showTargetDialog = false },
                onConfirm = {
                    onTargetSizeChange(it)
                    showTargetDialog = false
                },
            )
        }

        if (targetSizeMb != null) {
            Text(
                text = stringResource(R.string.video_target_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Text(
                stringResource(R.string.video_bitrate),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BitrateTier.entries.forEach { tier ->
                    FilterChip(
                        selected = preset.bitrate == tier,
                        onClick = { onBitrateChange(tier) },
                        enabled = enabled,
                        label = { Text(stringResource(tier.labelRes())) },
                    )
                }
            }
        }

        val bitrateLabel = stringResource(preset.bitrate.labelRes())
        val summary = stringResource(
            R.string.video_preset_summary,
            preset.resolution.label,
            bitrateLabel,
            preset.bitrateMbpsText(),
        )
        val detail = if (targetSizeMb != null) {
            stringResource(
                R.string.video_target_summary,
                targetSizeMb,
                frameRate,
                formatDuration(trimDurationMs),
            )
        } else {
            stringResource(
                R.string.video_preset_detail,
                summary,
                frameRate,
                formatDuration(trimDurationMs),
                ByteFormat.formatBytes(estimatedBytes),
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** 码率档 → 可翻译的显示名。 */
private const val DEFAULT_TARGET_MB = 16
private const val MIN_TARGET_MB = 5

@Composable
private fun TargetSizeDialog(
    initialMb: Int,
    maxMb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val minMb = if (maxMb < MIN_TARGET_MB) 1 else MIN_TARGET_MB
    var text by remember { mutableStateOf(initialMb.coerceIn(minMb, maxMb).toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in minMb..maxMb

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_target_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        if (input.length <= 4 && input.all { it.isDigit() }) text = input
                    },
                    label = { Text(stringResource(R.string.video_target_input_label)) },
                    suffix = { Text("MB") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Text(
                    text = stringResource(R.string.video_target_range, minMb, maxMb),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = valid,
            ) {
                Text(stringResource(R.string.video_target_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.video_cancel))
            }
        },
    )
}

private fun BitrateTier.labelRes(): Int = when (this) {
    BitrateTier.HIGH -> R.string.video_bitrate_high
    BitrateTier.MEDIUM -> R.string.video_bitrate_medium
    BitrateTier.LOW -> R.string.video_bitrate_low
}

/** mm:ss（超过 1 小时则 h:mm:ss）。 */
fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
