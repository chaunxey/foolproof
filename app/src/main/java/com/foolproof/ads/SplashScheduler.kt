package com.foolproof.ads

import android.content.Context
import com.foolproof.common.storage.LocalStore

/**
 * 启动/唤醒广告的时机判断：
 * - 进程首次启动必定展示一次；
 * - 之后从后台回到前台，若离开超过 1 小时则展示。
 */
object SplashScheduler {

    private const val KEY_LAST_BG = "last_background_at"
    private const val ONE_HOUR_MS = 60 * 60 * 1000L

    private var coldStartPending = true
    private var lastBackgroundAt = 0L

    fun init(context: Context) {
        LocalStore.init(context.applicationContext)
        lastBackgroundAt = LocalStore.readLong(KEY_LAST_BG, 0L)
    }

    fun shouldShow(): Boolean {
        if (coldStartPending) {
            coldStartPending = false
            return true
        }
        return lastBackgroundAt > 0L &&
            System.currentTimeMillis() - lastBackgroundAt >= ONE_HOUR_MS
    }

    fun recordBackground() {
        val now = System.currentTimeMillis()
        lastBackgroundAt = now
        LocalStore.writeLong(KEY_LAST_BG, now)
    }
}
