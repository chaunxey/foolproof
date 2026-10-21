package com.foolproof.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.foolproof.R
import kotlin.math.roundToInt

/**
 * 设置页：字体大小 + 语言。
 *
 * 两者都即时生效：字体改 Density.fontScale；语言替换 CompositionLocal 里的
 * Context/Configuration，**不重建 Activity**。选完语言直接关闭弹窗回到首页。
 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val scale by SettingsStore.fontScale.collectAsState()
    val language by SettingsStore.language.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleMedium)

                Text(
                    text = stringResource(R.string.settings_font_size, (scale * 100).roundToInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Slider(
                    value = scale,
                    onValueChange = { SettingsStore.setFontScale(it) },
                    valueRange = SettingsStore.MIN_SCALE..SettingsStore.MAX_SCALE,
                )

                Text(
                    text = stringResource(R.string.settings_font_preview),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
                LanguageChips(
                    current = language,
                    onSelect = {
                        SettingsStore.setLanguage(it)
                        // 需求：切换语言后回到首页
                        onDismiss()
                    },
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = { SettingsStore.setFontScale(1.0f) }) {
                        Text(stringResource(R.string.settings_restore_default))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_close)) }
                }
            }
        }
    }
}

/** 6 个选项用 FlowRow，窄屏自动换行。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips(current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppLanguage.entries.forEach { option ->
            FilterChip(
                selected = current == option,
                onClick = { if (current != option) onSelect(option) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}
