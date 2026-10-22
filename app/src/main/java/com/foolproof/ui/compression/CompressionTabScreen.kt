package com.foolproof.ui.compression

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foolproof.ads.SectionBannerAd
import com.foolproof.features.compression.CompressionSection
import com.foolproof.features.compression.CompressionViewModel
import com.foolproof.features.compression.compressionResultRows
import com.foolproof.features.duplicates.DuplicateAction
import com.foolproof.features.duplicates.DuplicateHeader
import com.foolproof.features.duplicates.DuplicateViewModel
import com.foolproof.features.duplicates.ui.duplicatePhotoRows
import org.koin.compose.viewmodel.koinViewModel

/**
 * 图片压缩 tab 的容器。
 *
 * 宪法 §3.2（v1.8 / v1.12）：整页由 `:app` 组装，**图片压缩区在上、重复照片清理区在下**，
 * 且**整页只有一个滚动/懒加载容器**——就是这里的 `LazyVerticalGrid`：
 *
 * - 压缩区作为一个整行 item（它自己的列表很小，且不是滚动容器）；
 * - 清理区的组头与图片行由 feature 以 `duplicatePhotoRows(...)` 贡献 item，逐行懒加载，
 *   避免"一次解码上百张缩略图"导致的掉帧。
 *
 * 本文件只做布局与组合，不得出现业务逻辑（宪法 §9 一票否决项）。
 */
@Composable
fun CompressionTabScreen(modifier: Modifier = Modifier) {
    val compressionViewModel: CompressionViewModel = koinViewModel()
    val compressionState by compressionViewModel.uiState.collectAsState()
    val duplicateViewModel: DuplicateViewModel = koinViewModel()
    val duplicateState by duplicateViewModel.uiState.collectAsState()

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(
            key = "compression",
            contentType = "compression-header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            CompressionSection(
                viewModel = compressionViewModel,
                renderResults = false,
            )
        }

        compressionResultRows(
            state = compressionState,
            viewModel = compressionViewModel,
        )

        // 压缩区与去重区之间的横幅广告
        item(
            key = "ad-banner",
            contentType = "ad-banner",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            SectionBannerAd()
        }

        item(
            key = "divider",
            contentType = "divider",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            HorizontalDivider()
        }

        item(
            key = "duplicate-header",
            contentType = "duplicate-header",
            span = { GridItemSpan(maxLineSpan) },
        ) {
            DuplicateHeader(viewModel = duplicateViewModel)
        }

        duplicatePhotoRows(
            state = duplicateState,
            onToggle = { duplicateViewModel.dispatch(DuplicateAction.Toggle(it)) },
            onOpen = { duplicateViewModel.dispatch(DuplicateAction.OpenPreview(it)) },
        )
    }
}
