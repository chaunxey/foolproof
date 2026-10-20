package com.foolproof.ui.compression

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foolproof.features.compression.CompressionSection

/**
 * 图片压缩 tab 的容器。
 *
 * 宪法 §3.2 + v1.4 定案：整页由 `:app` 组装，feature 只提供 Section；
 * 纵向顺序为「图片压缩在上，重复照片清理在下」，后者待其模块建成后追加。
 *
 * 本文件只做布局与组合，不得出现任何压缩业务逻辑（宪法 §9 一票否决项）。
 */
@Composable
fun CompressionTabScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        CompressionSection(modifier = Modifier.weight(1f))

        // 预留：重复照片清理区（:features:duplicate-cleanup）将放在这里
    }
}
