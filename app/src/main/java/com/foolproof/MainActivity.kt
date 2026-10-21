package com.foolproof

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import com.foolproof.ads.SplashAdOverlay
import com.foolproof.ads.SplashScheduler
import com.foolproof.base.share.SharedContentStore
import com.foolproof.ui.FoolproofScreen
import com.foolproof.ui.settings.SettingsStore
import com.foolproof.ui.settings.ThemeMode
import com.foolproof.ui.theme.FoolproofTheme

class MainActivity : ComponentActivity() {

    private var splashChecked = false
    private val showSplash = mutableStateOf(false)

    override fun attachBaseContext(newBase: Context) {
        // 在 super 之前初始化偏好，保证首帧就能读到语言/字体/主题设置
        SettingsStore.init(newBase)
        // Activity 自身 resources 也跟随语言：Dialog 等新窗口不会掉回系统/旧语言
        super.attachBaseContext(SettingsStore.wrapBaseContext(newBase))
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SharedContentStore.publishFrom(this, intent)
    }

    override fun onStart() {
        super.onStart()
        if (!splashChecked) {
            splashChecked = true
            // 冷启动必看一次；之后只有后台超过 1 小时回来才看
            showSplash.value = SplashScheduler.shouldShow()
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) {
            SplashScheduler.recordBackground()
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SharedContentStore.publishFrom(this, intent)
        enableEdgeToEdge()
        setContent {
            val scale by SettingsStore.fontScale.collectAsState()
            val language by SettingsStore.language.collectAsState()
            val themeMode by SettingsStore.themeMode.collectAsState()

            // 语言切换只替换 Composable 树里的 Context/Configuration：
            // 不调用 recreate()，也不触发系统 per-app locale，所以是「无感」的。
            val activityContext = LocalContext.current
            val localizedContext = remember(language, activityContext) {
                SettingsStore.localizedContext(activityContext, language)
            }
            val base = LocalDensity.current

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            // 状态栏/导航栏图标明暗要跟着 App 主题走，不能只看系统 uiMode
            val view = LocalView.current
            SideEffect {
                val activity = view.context.findActivity() ?: return@SideEffect
                WindowCompat.getInsetsController(activity.window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                // 只改 Density.fontScale：文字随之缩放，切换无闪烁
                LocalDensity provides Density(density = base.density, fontScale = scale),
            ) {
                FoolproofTheme(darkTheme = darkTheme) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        FoolproofScreen(modifier = Modifier.fillMaxSize())
                        if (showSplash.value) {
                            SplashAdOverlay(onFinished = { showSplash.value = false })
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
