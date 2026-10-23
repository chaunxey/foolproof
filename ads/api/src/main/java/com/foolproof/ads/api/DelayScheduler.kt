package com.foolproof.ads.api

/** 可注入的延时器（默认实现见 [HandlerDelayScheduler]），便于 JVM 单测。 */
interface DelayScheduler {
    fun postDelayed(delayMs: Long, block: () -> Unit)

    fun cancel()
}
