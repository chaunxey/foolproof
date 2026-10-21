package com.foolproof.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.foolproof.features.video.VideoCompressionSection
import com.foolproof.ui.compression.CompressionTabScreen

/**
 * 应用主界面：底部两个 tab（图片 / 视频）。
 *
 * 宪法 §3 / §9：`:app` 只做布局与组合，不写业务逻辑。
 * 宪法 §3.2 v1.11：每个 tab 内部**只有一个滚动容器**——图片 tab 由
 * `CompressionTabScreen` 持有，视频 tab 由这里的 `verticalScroll` 持有。
 */
@Composable
fun FoolproofScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("图片") },
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("视频") },
                )
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            0 -> CompressionTabScreen(modifier = Modifier.padding(innerPadding))
            else -> VideoTabScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun VideoTabScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        VideoCompressionSection()
    }
}
