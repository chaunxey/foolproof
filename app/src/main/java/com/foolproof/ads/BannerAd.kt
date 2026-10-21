package com.foolproof.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.facebook.ads.AdSize
import com.facebook.ads.AdView
import kotlinx.coroutines.delay

/**
 * 横幅广告（320×50 自适应宽度）。每 60 秒重新 load 一次，相当于轮播刷新。
 * 未配置 placement ID 时显示占位块，方便先调布局。
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (!AdsConfig.configured) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "AD",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    var adView by remember { mutableStateOf<AdView?>(null) }
    AndroidView(
        modifier = modifier.fillMaxWidth().height(50.dp),
        factory = { context ->
            AdView(context, AdsConfig.BANNER_PLACEMENT_ID, AdSize.BANNER_HEIGHT_50).apply {
                loadAd()
                adView = this
            }
        },
    )

    LaunchedEffect(adView) {
        while (adView != null) {
            delay(60_000)
            adView?.loadAd()
        }
    }
}

/** 启动/唤醒时的 5 秒全屏广告层：上面是 banner，下面是倒计时。 */
@Composable
fun SplashAdOverlay(onFinished: () -> Unit) {
    var secondsLeft by remember { mutableStateOf(5) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
        onFinished()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BannerAd(modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                text = "$secondsLeft",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}
