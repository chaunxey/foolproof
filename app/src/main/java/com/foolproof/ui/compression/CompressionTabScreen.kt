package com.foolproof.ui.compression

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foolproof.features.compression.CompressionSection
import com.foolproof.features.duplicates.DuplicateSection

/**
 * 图片压缩 tab 的容器。
 *
 * 宪法 §3.2 + v1.4/v1.8：整页由 `:app` 组装，**图片压缩区在上、重复照片清理区在下**；
 * 两个 feature 各自只提供 Section，互不依赖。
 *
 * 本文件只做布局与组合，不得出现任何业务逻辑（宪法 §9 一票否决项）。
 */
@Composable
fun CompressionTabScreen(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize()) {
        CompressionSection(modifier = Modifier.weight(1f))
        DuplicateSection(modifier = Modifier.weight(1f))
    }
}
