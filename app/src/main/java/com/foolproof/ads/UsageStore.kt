package com.foolproof.ads

import android.content.Context
import com.foolproof.base.ads.FeatureId

/** 每个功能「成功使用次数」的持久化计数（SharedPreferences 足够）。 */
object UsageStore {

    private const val PREFS = "ad_usage"
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun uses(feature: FeatureId): Int = prefs.getInt(key(feature), 0)

    fun increment(feature: FeatureId) {
        prefs.edit().putInt(key(feature), uses(feature) + 1).apply()
    }

    fun reset(feature: FeatureId) {
        prefs.edit().remove(key(feature)).apply()
    }

    private fun key(feature: FeatureId): String = "uses_${feature.name}"
}
