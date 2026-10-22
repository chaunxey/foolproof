package com.foolproof.ui.video

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.foolproof.R
import com.foolproof.ads.SectionBannerAd
import com.foolproof.features.video.VideoCompressionSection
import com.foolproof.features.video.VideoMergeSection
import com.foolproof.features.video.VideoTrimSection
import kotlinx.coroutines.launch

private enum class VideoTabMode { COMPRESS, MERGE, TRIM }

/**
 * 视频 tab：压缩 / 拼接二选一，避免两个完整表单纵向堆叠。
 * 广告位放在模式切换下方，作为功能分割处的横幅。
 */
@Composable
fun VideoTabScreen(
    modifier: Modifier = Modifier,
    onPageChanged: (Int) -> Unit = {},
) {
    var mode by rememberSaveable { mutableStateOf(VideoTabMode.COMPRESS) }
    val pagerState = rememberPagerState(initialPage = mode.ordinal) {
        VideoTabMode.entries.size
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        mode = VideoTabMode.entries[pagerState.currentPage]
        onPageChanged(pagerState.currentPage)
    }

    Column(modifier = modifier.fillMaxSize()) {
        ModeSwitch(
            mode = mode,
            onModeChange = { next ->
                scope.launch { pagerState.animateScrollToPage(next.ordinal) }
            },
        )
        SectionBannerAd()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
        ) { page ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                when (VideoTabMode.entries[page]) {
                    VideoTabMode.COMPRESS -> VideoCompressionSection()
                    VideoTabMode.MERGE -> VideoMergeSection()
                    VideoTabMode.TRIM -> VideoTrimSection()
                }
            }
        }
    }
}

/** 自绘分段控件：选中态用主色填充，没有系统 SegmentedButton 的对勾。 */
@Composable
private fun ModeSwitch(mode: VideoTabMode, onModeChange: (VideoTabMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeSegment(
            text = stringResource(R.string.video_mode_compress),
            selected = mode == VideoTabMode.COMPRESS,
            onClick = { onModeChange(VideoTabMode.COMPRESS) },
            modifier = Modifier.weight(1f),
        )
        ModeSegment(
            text = stringResource(R.string.video_mode_merge),
            selected = mode == VideoTabMode.MERGE,
            onClick = { onModeChange(VideoTabMode.MERGE) },
            modifier = Modifier.weight(1f),
        )
        ModeSegment(
            text = stringResource(R.string.video_mode_trim),
            selected = mode == VideoTabMode.TRIM,
            onClick = { onModeChange(VideoTabMode.TRIM) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "mode-bg",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
