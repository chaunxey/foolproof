package com.foolproof.common.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.foolproof.common.R

/**
 * 底部导航的目的地（宪法 §3 / v1.17）。
 *
 * 顺序即 tab 顺序；语音聊天 tab 后续追加在这里，不改业务模块。
 */
enum class FoolproofDestination(val labelRes: Int, val iconRes: Int) {
    Images(R.string.tab_images, R.drawable.ic_tab_images),
    Videos(R.string.tab_videos, R.drawable.ic_tab_videos),
    Converter(R.string.tab_converter, R.drawable.ic_tab_converter),
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
    onMenuClick: (() -> Unit)? = null,
    requestedDestination: FoolproofDestination? = null,
    onDestinationChanged: ((FoolproofDestination) -> Unit)? = null,
    content: @Composable (FoolproofDestination) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(FoolproofDestination.Images.name) }
    LaunchedEffect(requestedDestination) {
        requestedDestination?.let { selected = it.name }
    }
    val current = destinations.firstOrNull { it.name == selected } ?: destinations.first()
    LaunchedEffect(current) { onDestinationChanged?.invoke(current) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (onMenuClick != null) {
                Row(
                    // 应用是 edge-to-edge（MainActivity 调了 enableEdgeToEdge），
                    // 顶栏必须自己避开状态栏，否则菜单键会压在状态栏图标上。
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    IconButton(onClick = onMenuClick) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = stringResource(R.string.tab_menu),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        selected = destination == current,
                        onClick = { selected = destination.name },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            content(current)
        }
    }
}
