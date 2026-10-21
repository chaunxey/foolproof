package com.foolproof.ui.conversion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foolproof.R
import com.foolproof.ads.SectionBannerAd
import com.foolproof.base.share.SharedContentStore
import com.foolproof.features.conversion.ui.FileConversionSection
import com.foolproof.features.conversion.ui.PdfToolsSection
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect

private enum class ConversionTabMode { GENERATE, TOOLS }

/** 转换 tab：生成 PDF / PDF 工具二选一。 */
@Composable
fun ConversionTabScreen(modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableStateOf(ConversionTabMode.GENERATE) }
    val shared by SharedContentStore.content.collectAsState()
    LaunchedEffect(shared) {
        if (shared?.kind == SharedContentStore.Kind.PDF) {
            mode = ConversionTabMode.TOOLS
        }
    }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            FilterChip(
                selected = mode == ConversionTabMode.GENERATE,
                onClick = { mode = ConversionTabMode.GENERATE },
                label = { Text(stringResource(R.string.conversion_tab_generate)) },
            )
            FilterChip(
                selected = mode == ConversionTabMode.TOOLS,
                onClick = { mode = ConversionTabMode.TOOLS },
                label = { Text(stringResource(R.string.conversion_tab_tools)) },
            )
        }

        SectionBannerAd()

        when (mode) {
            ConversionTabMode.GENERATE -> FileConversionSection()
            ConversionTabMode.TOOLS -> PdfToolsSection()
        }
    }
}
