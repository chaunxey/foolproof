package com.foolproof

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.foolproof.ui.FoolproofScreen
import com.foolproof.ui.settings.SettingsStore
import com.foolproof.ui.theme.FoolproofTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        enableEdgeToEdge()
        setContent {
            // 只改 Density.fontScale：文字随之缩放，Activity 不重建，切换无闪烁
            val scale by SettingsStore.fontScale.collectAsState()
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = scale),
            ) {
                FoolproofTheme {
                    FoolproofScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}
