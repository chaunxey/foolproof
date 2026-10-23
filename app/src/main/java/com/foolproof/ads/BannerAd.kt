package com.foolproof.ads

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.foolproof.ads.api.AdSdkRegistry
import com.foolproof.ads.api.SplashAdSlot
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

private const val BANNER_REFRESH_MS = 60_000L
private const val SPLASH_WATCHDOG_MS = 2_000L
private const val SPLASH_MAX_MS = 6_000L
private const val SPLASH_SECONDS = 5
private const val SPLASH_SKIP_AFTER_SECONDS = 3

/**
 * 功能分割处的横幅（320×50）。SDK 由构建开关决定：
 * - 未配置 / 加载失败 → 占位块；
 * - 可见期间每 60 秒刷新一次；
 * - 离开页面时释放原生 View。
 */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sdk = AdSdkRegistry.sdk
    var loaded by remember(sdk) { mutableStateOf(false) }
    val slot = remember(sdk) {
        sdk?.createBanner(context) { loaded = it }
    }

    Box(modifier = modifier.fillMaxWidth().height(50.dp)) {
        if (slot == null) {
            PlaceholderBanner(Modifier.fillMaxSize())
        } else {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { slot.view })
            // 加载前用固定高度骨架占位，避免真实广告出现时布局跳动
            if (!loaded) {
                PlaceholderBanner(Modifier.fillMaxSize())
            }
            LaunchedEffect(slot) {
                while (true) {
                    delay(BANNER_REFRESH_MS)
                    slot.refresh()
                }
            }
            DisposableEffect(slot) {
                onDispose { slot.destroy() }
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

/** 功能分割处的横幅：全局内容区已有 16dp 横向 padding，这里只留上下 8dp。 */
@Composable
fun SectionBannerAd(modifier: Modifier = Modifier) {
    BannerAd(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp))
}

/**
 * 启动/唤醒广告层。
 *
 * - 穿山甲：先尝试原生开屏（SDK 自带倒计时/跳过），2 秒内没就绪就回退；
 * - Meta / 未配置 / 加载失败：回退到自绘 5 秒壳（横幅 + 3 秒后可跳过）。
 */
@Composable
fun SplashAdOverlay(onFinished: () -> Unit, onReady: () -> Unit = {}) {
    val context = LocalContext.current
    val sdk = AdSdkRegistry.sdk
    val settled = remember { AtomicBoolean(false) }
    var state by remember { mutableStateOf<SplashState>(SplashState.Loading) }

    // 原生广告/自绘壳一旦就绪，通知 Activity 释放系统启动画面
    LaunchedEffect(state) {
        if (state !is SplashState.Loading) onReady()
    }

    LaunchedEffect(sdk) {
        if (sdk == null) {
            state = SplashState.Shell
            return@LaunchedEffect
        }
        sdk.loadSplash(
            context,
            onReady = { slot ->
                if (settled.compareAndSet(false, true)) {
                    state = SplashState.Native(slot)
                } else {
                    // 已经回退到自绘壳，迟到的原生开屏直接丢弃，避免二次闪屏
                    slot.destroy()
                }
            },
            onUnavailable = {
                if (settled.compareAndSet(false, true)) {
                    state = SplashState.Shell
                }
            },
        )
        // 看门狗：SDK 迟迟不回调时不要让用户一直看黑屏
        delay(SPLASH_WATCHDOG_MS)
        if (settled.compareAndSet(false, true)) {
            state = SplashState.Shell
        }
    }

    when (val current = state) {
        SplashState.Loading -> SplashLoading()
        SplashState.Shell -> SplashShell(onFinished)
        is SplashState.Native -> NativeSplash(current.slot, onFinished)
    }
}

/**
 * 开屏加载态：只保留与系统启动画面一致的底色。
 *
 * 系统启动画面负责显示图标；这里不再画第二个图标，避免尺寸/白边不一致。
 */
@Composable
private fun SplashLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(com.foolproof.R.color.splash_background)),
    )
}

private sealed interface SplashState {
    data object Loading : SplashState

    data object Shell : SplashState

    data class Native(val slot: SplashAdSlot) : SplashState
}

@Composable
private fun NativeSplash(slot: SplashAdSlot, onFinished: () -> Unit) {
    DisposableEffect(slot) {
        onDispose { slot.destroy() }
    }
    LaunchedEffect(slot) {
        slot.setOnCloseListener { onFinished() }
        // 硬上限：SDK 异常不回调时也要能进 App
        delay(SPLASH_MAX_MS)
        onFinished()
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { slot.view })
    }
}

/** 自绘开屏壳：5 秒倒计时，3 秒后出现【跳过】（Meta 与降级路径使用）。 */
@Composable
private fun SplashShell(onFinished: () -> Unit) {
    var secondsLeft by remember { mutableStateOf(SPLASH_SECONDS) }

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

        // 倒计时阶段只显示秒数，不出现「跳过」；倒计时结束后才出现可点按钮
        val elapsed = SPLASH_SECONDS - secondsLeft
        val skipRemaining = (SPLASH_SKIP_AFTER_SECONDS - elapsed).coerceAtLeast(0)
        if (skipRemaining > 0) {
            Text(
                text = "$skipRemaining",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        } else {
            TextButton(
                onClick = onFinished,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(com.foolproof.R.string.ads_skip),
                    color = Color.White,
                )
            }
        }
    }
}
