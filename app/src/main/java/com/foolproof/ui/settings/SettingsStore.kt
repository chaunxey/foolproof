package com.foolproof.ui.settings

import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用语言。
 *
 * [tag] 为 null 表示跟随系统（用系统 locale 选 `values-*`）；否则用它覆盖资源 locale。
 */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    CHINESE("zh"),
    ENGLISH("en"),
}

/**
 * 应用内设置（字体大小、语言）。
 *
 * 用 SharedPreferences 而非 DataStore：只存两个小值，不值得为此加依赖。
 * 字体大小用 StateFlow 暴露、切换后 Compose 立即重组，**不重建 Activity**；
 * 语言必须在 Activity 创建前生效，所以由 `MainActivity.attachBaseContext` 读取。
 */
object SettingsStore {

    private const val PREFS = "app_settings"
    private const val KEY_SCALE = "font_scale"
    private const val KEY_LANGUAGE = "language"
    const val MIN_SCALE = 0.85f
    const val MAX_SCALE = 1.3f

    private lateinit var prefs: android.content.SharedPreferences

    private val _fontScale = MutableStateFlow(1.0f)
    val fontScale: StateFlow<Float> = _fontScale

    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = _language

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _fontScale.value = prefs.getFloat(KEY_SCALE, 1.0f).coerceIn(MIN_SCALE, MAX_SCALE)
        _language.value = prefs.getString(KEY_LANGUAGE, null)
            ?.let { tag -> AppLanguage.entries.firstOrNull { it.tag == tag } }
            ?: AppLanguage.SYSTEM
    }

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(MIN_SCALE, MAX_SCALE)
        _fontScale.value = clamped
        if (this::prefs.isInitialized) {
            prefs.edit().putFloat(KEY_SCALE, clamped).apply()
        }
    }

    fun setLanguage(language: AppLanguage) {
        _language.value = language
        if (this::prefs.isInitialized) {
            // putString(key, null) 等价于 remove，SYSTEM 不落盘覆盖值
            prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
        }
    }

    /**
     * 按当前语言设置包一层 Context，供 `attachBaseContext` 使用。
     * 跟随系统时直接返回原 Context，由系统 locale 决定加载哪套 `values-*`。
     */
    fun localizedContext(base: Context): Context {
        val tag = _language.value.tag ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(tag))
        return base.createConfigurationContext(configuration)
    }
}
