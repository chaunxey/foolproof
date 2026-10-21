package com.foolproof.features.video.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foolproof.base.util.ByteFormat

/**
 * 压缩结果预览弹窗。
 *
 * 为什么用弹窗而不是在原页面切换：用户是"临时看一眼结果"，看完就关，
 * 不应该让主页面进入另一种状态（那样还要考虑怎么切回去、状态如何重置）。
 */
@Composable
internal fun ResultPreviewDialog(
    path: String,
    bytes: Long,
    originalBytes: Long,
    durationMs: Long,
    videoAspect: Float,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        // 默认宽度太窄，视频看不清
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            // 大屏上不要铺满，保持"弹窗"的观感
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 420.dp)
                .fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("压缩结果", style = MaterialTheme.typography.titleMedium)

                Text(
                    text = buildString {
                        if (originalBytes > 0) {
                            append(ByteFormat.formatBytes(originalBytes))
                            append(" → ")
                        }
                        append(ByteFormat.formatBytes(bytes))
                        append(" · 时长 ")
                        append(formatDuration(durationMs))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )

                VideoPreview(
                    uri = "file://$path",
                    startMs = 0L,
                    endMs = durationMs,
                    videoAspect = videoAspect,
                    modifier = Modifier.padding(top = 12.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }
        }
    }
}
