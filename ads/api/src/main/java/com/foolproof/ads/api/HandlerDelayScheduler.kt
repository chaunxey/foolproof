package com.foolproof.ads.api

import android.os.Handler
import android.os.Looper

/** 主线程延时器：门控超时兜底用。 */
class HandlerDelayScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : DelayScheduler {

    private var pending: Runnable? = null

    override fun postDelayed(delayMs: Long, block: () -> Unit) {
        val runnable = Runnable { block() }
        pending = runnable
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }
}
