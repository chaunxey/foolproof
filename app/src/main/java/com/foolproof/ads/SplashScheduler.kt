package com.foolproof.ads

import android.content.Context

/**
 * 启动/唤醒广告的时机判断：
 * - 进程首次启动必定展示一次；
 * - 之后从后台回到前台，若离开超过 1 小时则展示。
 */
object SplashScheduler {

    private const val PREFS = "ad_splash"
    private const val KEY_LAST_BG = "last_background_at"
    private const val ONE_HOUR_MS = 60 * 60 * 1000L

    private lateinit var prefs: android.content.SharedPreferences
    private var coldStartPending = true

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun shouldShow(): Boolean {
        if (coldStartPending) {
            coldStartPending = false
            return true
        }
        val last = prefs.getLong(KEY_LAST_BG, 0L)
        return last > 0L && System.currentTimeMillis() - last >= ONE_HOUR_MS
    }

    fun recordBackground() {
        prefs.edit().putLong(KEY_LAST_BG, System.currentTimeMillis()).apply()
    }
}
