package com.foolproof.ads

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var loaded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth().height(50.dp)) {
        if (!AdsConfig.configured) {
            PlaceholderBanner(Modifier.fillMaxSize())
        } else {
            var adView by remember { mutableStateOf<AdView?>(null) }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    AdView(context, AdsConfig.BANNER_PLACEMENT_ID, AdSize.BANNER_HEIGHT_50).apply {
                        val listener = object : com.facebook.ads.AdListener {
                            override fun onError(ad: com.facebook.ads.Ad, error: com.facebook.ads.AdError) {
                                loaded = false
                            }

                            override fun onAdLoaded(ad: com.facebook.ads.Ad) {
                                loaded = true
                            }

                            override fun onAdClicked(ad: com.facebook.ads.Ad) = Unit

                            override fun onLoggingImpression(ad: com.facebook.ads.Ad) = Unit
                        }
                        loadAd(buildLoadAdConfig().withAdListener(listener).build())
                        adView = this
                    }
                },
            )
            // 广告加载前用固定高度骨架占位，避免真实广告出现时布局跳动
            if (!loaded) {
                PlaceholderBanner(Modifier.fillMaxSize())
            }
            LaunchedEffect(adView) {
                while (adView != null) {
                    delay(60_000)
                    adView?.loadAd()
                }
            }
        }
    }
}

@Composable
private fun PlaceholderBanner(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "AD",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** 功能分割处的横幅：左右留 16dp、上下 8dp。新增功能分段时直接复用。 */
@Composable
fun SectionBannerAd(modifier: Modifier = Modifier) {
    BannerAd(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
}

/** 启动/唤醒时的 5 秒全屏广告层：3 秒后右上角可跳过。 */
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BannerAd(modifier = Modifier.padding(horizontal = 16.dp))
            Text(
                text = "$secondsLeft",
                color = Color.White,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        // 右上角始终显示倒计时；前 3 秒不可点，之后可跳过
        val canSkip = secondsLeft <= 2
        TextButton(
            onClick = onFinished,
            enabled = canSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Text(
                text = androidx.compose.ui.res.stringResource(
                    com.foolproof.R.string.ads_skip_countdown,
                    secondsLeft,
                ),
                color = Color.White.copy(alpha = if (canSkip) 1f else 0.5f),
            )
        }
    }
}
