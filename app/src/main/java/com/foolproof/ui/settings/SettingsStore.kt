package com.foolproof.ui.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.foolproof.R
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 应用语言。
 *
 * [tag] 为 null 表示跟随系统；否则用它覆盖资源 locale。
 * [labelRes] 是设置页里的显示名：每种语言都用**它自己的写法**，不随界面语言变化。
 */
enum class AppLanguage(val tag: String?, val labelRes: Int) {
    SYSTEM(null, R.string.settings_language_system),
    CHINESE("zh", R.string.settings_language_zh),
    ENGLISH("en", R.string.settings_language_en),
    KHMER("km", R.string.settings_language_km),
    JAPANESE("ja", R.string.settings_language_ja),
    FRENCH("fr", R.string.settings_language_fr),
    GERMAN("de", R.string.settings_language_de),
}

/**
 * 应用内设置（字体大小、语言）。
 *
 * 用 SharedPreferences 而非 DataStore：只存两个小值，不值得为此加依赖。
 * 两者都通过 StateFlow 暴露，由 Compose 层直接消费——**切换不重建 Activity**。
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

    /** 只写偏好；界面由 `MainActivity` 里的 CompositionLocal 立即重组，不重建 Activity。 */
    fun setLanguage(language: AppLanguage) {
        _language.value = language
        if (this::prefs.isInitialized) {
            // putString(key, null) 等价于 remove，SYSTEM 不落盘覆盖值
            prefs.edit().putString(KEY_LANGUAGE, language.tag).apply()
        }
    }

    /**
     * 按语言包一层 Context，供 Compose 的 LocalContext 使用。
     * 跟随系统时直接返回原 Context，由系统 locale 决定加载哪套 `values-*`。
     *
     * 注意不能直接返回 `createConfigurationContext(...)`：那样 Compose 会顺着
     * Context 链找不到 Activity，`rememberLauncherForActivityResult` 会抛
     * “No ActivityResultRegistryOwner”。这里用 [ContextWrapper] 保留 Activity 链，
     * 只覆盖 resources。
     */
    fun localizedContext(base: Context, language: AppLanguage = _language.value): Context {
        val tag = language.tag ?: return base
        return LocalizedContext(base, Locale.forLanguageTag(tag))
    }

    private class LocalizedContext(base: Context, locale: Locale) : ContextWrapper(base) {

        private val localizedResources: Resources = base.createConfigurationContext(
            Configuration(base.resources.configuration).apply { setLocale(locale) },
        ).resources

        override fun getResources(): Resources = localizedResources
    }
}
