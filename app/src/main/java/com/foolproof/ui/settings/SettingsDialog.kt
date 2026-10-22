package com.foolproof.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foolproof.R
import com.foolproof.ui.auth.AuthStore
import kotlin.math.roundToInt

/**
 * 设置页：字体大小 / 语言 / 主题。
 *
 * 三者都即时生效：字体改 Density.fontScale；语言替换 CompositionLocal 里的
 * Context/Configuration，**不重建 Activity**。选完语言直接返回首页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_close),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val phone by AuthStore.phone.collectAsState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SettingsContent(
                modifier = Modifier.weight(1f),
                onClose = onBack,
            )
            if (phone != null) {
                HorizontalDivider()
                TextButton(
                    onClick = { AuthStore.logout() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.auth_logout),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    onClose: () -> Unit,
) {
    val scale by SettingsStore.fontScale.collectAsState()
    val language by SettingsStore.language.collectAsState()
    val themeMode by SettingsStore.themeMode.collectAsState()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_font_size, (scale * 100).roundToInt()),
            style = MaterialTheme.typography.bodyMedium,
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
        TextButton(
            onClick = { SettingsStore.resetFontScale() },
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Text(stringResource(R.string.settings_restore_default))
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_language),
            style = MaterialTheme.typography.bodyMedium,
        )
        LanguageChips(
            current = language,
            onSelect = {
                SettingsStore.setLanguage(it)
                // 选完语言直接回首页
                onClose()
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyMedium,
        )
        ThemeChips(
            current = themeMode,
            onSelect = { SettingsStore.setThemeMode(it) },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.settings_close)) }
        }
    }
}

/** 6 个语言选项用 FlowRow，窄屏自动换行。 */
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

/** 明暗主题：即时生效，页面不关闭，方便直接看效果。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeChips(current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeMode.entries.forEach { option ->
            FilterChip(
                selected = current == option,
                onClick = { if (current != option) onSelect(option) },
                label = { Text(stringResource(option.labelRes)) },
            )
        }
    }
}
