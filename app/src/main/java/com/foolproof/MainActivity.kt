package com.foolproof

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.foolproof.ui.FoolproofScreen
import com.foolproof.ui.settings.SettingsStore
import com.foolproof.ui.theme.FoolproofTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // 在 super 之前初始化偏好，保证首帧就能读到语言/字体设置
        SettingsStore.init(newBase)
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val scale by SettingsStore.fontScale.collectAsState()
            val language by SettingsStore.language.collectAsState()

            // 语言切换只替换 Composable 树里的 Context/Configuration：
            // 不调用 recreate()，也不触发系统 per-app locale，所以是「无感」的。
            val activityContext = LocalContext.current
            val localizedContext = remember(language, activityContext) {
                SettingsStore.localizedContext(activityContext, language)
            }
            val base = LocalDensity.current

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalConfiguration provides localizedContext.resources.configuration,
                // 只改 Density.fontScale：文字随之缩放，切换无闪烁
                LocalDensity provides Density(density = base.density, fontScale = scale),
            ) {
                FoolproofTheme {
                    FoolproofScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
