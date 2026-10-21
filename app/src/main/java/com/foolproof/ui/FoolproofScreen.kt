package com.foolproof.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.foolproof.R
import com.foolproof.ads.SectionBannerAd
import com.foolproof.ui.settings.SettingsDialog
import com.foolproof.common.navigation.FoolproofDestination
import com.foolproof.common.navigation.FoolproofTabHost
import com.foolproof.features.conversion.ui.FileConversionSection
import com.foolproof.features.video.VideoCompressionSection
import com.foolproof.features.video.VideoMergeSection
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
    // 防误触：返回键第一次只提示，2 秒内再按才退出
    val context = LocalContext.current
    val backPressAgain = stringResource(R.string.back_press_again)
    var lastBackAt by remember { mutableStateOf(0L) }
    BackHandler {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt <= 2_000L) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(context, backPressAgain, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) SettingsDialog(onDismiss = { showSettings = false })

    FoolproofTabHost(
        modifier = modifier.fillMaxSize(),
        onSettingsClick = { showSettings = true },
    ) { destination ->
        when (destination) {
            FoolproofDestination.Images -> CompressionTabScreen()
            FoolproofDestination.Videos -> ScrollableTab {
                VideoCompressionSection()
                // 视频压缩 / 拼接之间的功能分割广告位
                SectionBannerAd()
                VideoMergeSection()
            }
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
