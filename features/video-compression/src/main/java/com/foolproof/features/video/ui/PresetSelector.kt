package com.foolproof.features.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foolproof.base.util.ByteFormat
import com.foolproof.features.video.domain.BitrateTier
import com.foolproof.features.video.domain.CompressionPreset
import com.foolproof.features.video.domain.ResolutionTier
import java.util.Locale

/**
 * 档位选择器：分辨率三档 + 码率三档，并显示目标参数与预估体积。
 *
 * 纯展示组件（参数由外部传入），便于以后复用与预览。
 */
@Composable
internal fun PresetSelector(
    preset: CompressionPreset,
    estimatedBytes: Long,
    trimDurationMs: Long,
    frameRate: Int,
    onResolutionChange: (ResolutionTier) -> Unit,
    onBitrateChange: (BitrateTier) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("分辨率", style = MaterialTheme.typography.labelMedium)
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
            "码率",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BitrateTier.entries.forEach { tier ->
                FilterChip(
                    selected = preset.bitrate == tier,
                    onClick = { onBitrateChange(tier) },
                    enabled = enabled,
                    label = { Text(tier.label) },
                )
            }
        }

        Text(
            text = buildString {
                append(preset.summary())
                append(" · 帧率 ${frameRate}fps")
                append(" · 时长 ")
                append(formatDuration(trimDurationMs))
                append(" · 预估 ")
                append(ByteFormat.formatBytes(estimatedBytes))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
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
