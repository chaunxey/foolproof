package com.foolproof.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foolproof.common.navigation.FoolproofDestination
import com.foolproof.common.navigation.FoolproofTabHost
import com.foolproof.features.conversion.ui.FileConversionSection
import com.foolproof.features.video.VideoCompressionSection
import com.foolproof.ui.compression.CompressionTabScreen

/**
 * 应用主界面：底部三个 tab（图片 / 视频 / 转换）。
 *
 * 宪法 §9：`:app` 只做布局与组合，不写业务逻辑与导航状态机——
 * tab 切换由 `:common` 的 [FoolproofTabHost] 负责（宪法 §2 v1.17）。
 *
 * 宪法 §3.2：每个 tab 内部**只有一个滚动容器**——图片 tab 由 `CompressionTabScreen`
 * 持有网格，视频与转换 tab 由这里的 `verticalScroll` 持有。
 */
@Composable
fun FoolproofScreen(modifier: Modifier = Modifier) {
    FoolproofTabHost(modifier = modifier.fillMaxSize()) { destination ->
        when (destination) {
            FoolproofDestination.Images -> CompressionTabScreen()
            FoolproofDestination.Videos -> ScrollableTab { VideoCompressionSection() }
            FoolproofDestination.Converter -> ScrollableTab { FileConversionSection() }
        }
    }
}

@Composable
private fun ScrollableTab(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        content()
    }
}
