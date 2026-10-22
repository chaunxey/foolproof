package com.foolproof.ads.pangle

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.bytedance.sdk.openadsdk.CSJSplashAd
import com.foolproof.ads.api.SplashAdSlot

/**
 * 穿山甲原生开屏：SDK 把素材渲染进容器，跳过/倒计时结束回调 [setOnCloseListener]。
 */
internal class PangleSplashSlot(
    private val ad: CSJSplashAd,
    context: Context,
) : SplashAdSlot {

    private val container = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private var closeListener: (() -> Unit)? = null
    private var started = false
    private var destroyed = false

    override val view: View
        get() = container

    override fun setOnCloseListener(listener: () -> Unit) {
        closeListener = listener
        ad.setSplashAdListener(
            object : CSJSplashAd.SplashAdListener {
                override fun onSplashAdShow(ad: CSJSplashAd) = Unit

                override fun onSplashAdClick(ad: CSJSplashAd) = Unit

                override fun onSplashAdClose(ad: CSJSplashAd, closeType: Int) {
                    closeListener?.invoke()
                }
            },
        )
        if (started) return
        started = true
        // post 到容器上：确保 View 已经 attach，SDK 才能正确测量并渲染
        container.post {
            if (destroyed) return@post
            runCatching { ad.showSplashView(container) }
                .onFailure { closeListener?.invoke() }
        }
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        closeListener = null
        runCatching { container.removeAllViews() }
    }
}
