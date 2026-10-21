package com.foolproof.ui.video

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.foolproof.features.video.VideoCompressionSection
import com.foolproof.features.video.VideoMergeSection

private enum class VideoTabMode { COMPRESS, MERGE }

/**
 * 视频 tab：压缩 / 拼接二选一，避免两个完整表单纵向堆叠。
 * 广告位放在模式切换下方，作为功能分割处的横幅。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTabScreen(modifier: Modifier = Modifier) {
    var mode by rememberSaveable { mutableStateOf(VideoTabMode.COMPRESS) }

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SegmentedButton(
                selected = mode == VideoTabMode.COMPRESS,
                onClick = { mode = VideoTabMode.COMPRESS },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text(stringResource(R.string.video_mode_compress))
            }
            SegmentedButton(
                selected = mode == VideoTabMode.MERGE,
                onClick = { mode = VideoTabMode.MERGE },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text(stringResource(R.string.video_mode_merge))
            }
        }

        SectionBannerAd()

        when (mode) {
            VideoTabMode.COMPRESS -> VideoCompressionSection()
            VideoTabMode.MERGE -> VideoMergeSection()
        }
    }
}
