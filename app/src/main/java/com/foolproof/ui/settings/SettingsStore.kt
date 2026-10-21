package com.foolproof.ui.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用内设置（字体大小）。
 *
 * 用 SharedPreferences 而非 DataStore：只需存一个 float，不值得为此加依赖。
 * 用 StateFlow 暴露，切换后 Compose 立即重组——**不重建 Activity**，因此无重启感。
 */
object SettingsStore {

    private const val PREFS = "app_settings"
    private const val KEY_SCALE = "font_scale"
    const val MIN_SCALE = 0.85f
    const val MAX_SCALE = 1.3f

    private lateinit var prefs: android.content.SharedPreferences

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _fontScale.value = prefs.getFloat(KEY_SCALE, 1.0f).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        _fontScale.value = clamped
        if (this::prefs.isInitialized) {
            prefs.edit().putFloat(KEY_SCALE, clamped).apply()
        }
    }
}
