package com.foolproof.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foolproof.R
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt

/** 设置页：字体大小 + 语言。字体即时生效；语言切换后重建 Activity 让资源重新加载。 */
@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    val scale by SettingsStore.fontScale.collectAsState()
    val language by SettingsStore.language.collectAsState()
    val context = LocalContext.current

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
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppLanguage.entries.forEach { option ->
                        FilterChip(
                            selected = language == option,
                            onClick = {
                                if (language != option) {
                                    SettingsStore.setLanguage(context, option)
                                    // 33+ 由系统按 LocaleManager 重建；低版本自己重建才能重载资源
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                        context.findActivity()?.recreate()
                                    }
                                }
                            },
                            label = { Text(stringResource(option.labelRes())) },
                        )
                    }
                }

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

/** 语言选项 → 可翻译的显示名（总是用各自语言显示自己）。 */
private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.SYSTEM -> R.string.settings_language_system
    AppLanguage.CHINESE -> R.string.settings_language_zh
    AppLanguage.ENGLISH -> R.string.settings_language_en
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
