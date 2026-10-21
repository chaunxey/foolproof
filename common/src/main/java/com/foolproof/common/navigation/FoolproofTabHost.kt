package com.foolproof.common.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 底部导航的目的地（宪法 §3 / v1.17）。
 *
 * 顺序即 tab 顺序；语音聊天 tab 后续追加在这里，不改业务模块。
 */
enum class FoolproofDestination(val label: String) {
    Images("图片"),
    Videos("视频"),
    Converter("转换"),
}

/**
 * 底部 tab 导航封装（宪法 §2 v1.17：轻量 tab host，业务模块不得自写导航）。
 *
 * 宪法 §3 的两条行为要求由状态机天然满足：
 * - **防重复入栈**：点当前 tab 只是把状态设回同一个值，不会重建页面；
 * - **回首页重置**：切回第一个 tab 即回到初始页面。
 *
 * @param content 每个目的地对应的页面内容；`:app` 只负责把各 feature 的 Section 传进来。
 */
@Composable
fun FoolproofTabHost(
    modifier: Modifier = Modifier,
    destinations: List<FoolproofDestination> = FoolproofDestination.entries,
    content: @Composable (FoolproofDestination) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(FoolproofDestination.Images.name) }
    val current = destinations.firstOrNull { it.name == selected } ?: destinations.first()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { selected = destination.name },
                        icon = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            content(current)
        }
    }
}
