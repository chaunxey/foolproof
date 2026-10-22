package com.foolproof.ads.pangle

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTNativeExpressAd
import com.foolproof.ads.api.AdBannerSlot

/** 穿山甲模板横幅：请求 50dp 高，渲染成功后塞进容器；刷新前先销毁旧广告。 */
internal class PangleBannerSlot(
    context: Context,
    private val onLoaded: (Boolean) -> Unit,
) : AdBannerSlot {

    private val container = FrameLayout(context)
    private var expressAd: TTNativeExpressAd? = null
    private var destroyed = false

    override val view: View
        get() = container

    init {
        loadWhenReady(context, RETRY_COUNT)
    }

    /**
     * 横幅可能在 SDK 异步初始化完成前就进入组合（图片 tab 首屏），
     * 这里短轮询等待就绪，避免整场会话都只显示占位。
     */
    private fun loadWhenReady(context: Context, attemptsLeft: Int) {
        if (destroyed) return
        if (TTAdSdk.isSdkReady()) {
            load(context)
            return
        }
        if (attemptsLeft <= 0) {
            onLoaded(false)
            return
        }
        container.postDelayed({ loadWhenReady(context, attemptsLeft - 1) }, RETRY_INTERVAL_MS)
    }

    override fun refresh() {
        if (destroyed) return
        expressAd?.destroy()
        expressAd = null
        container.removeAllViews()
        load(container.context)
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        expressAd?.destroy()
        expressAd = null
        container.removeAllViews()
    }

    private fun load(context: Context) {
        val adNative = runCatching { TTAdSdk.getAdManager().createAdNative(context) }.getOrNull()
        if (adNative == null) {
            onLoaded(false)
            return
        }
        val dm = context.resources.displayMetrics
        val widthDp = (dm.widthPixels / dm.density - 32f).coerceAtLeast(320f)
        val adSlot = AdSlot.Builder()
            .setCodeId(PangleAdsConfig.BANNER_CODE_ID)
            .setExpressViewAcceptedSize(widthDp, BANNER_HEIGHT_DP)
            .build()
        adNative.loadBannerExpressAd(adSlot, object : TTAdNative.NativeExpressAdListener {
            override fun onError(code: Int, message: String?) {
                onLoaded(false)
            }

            override fun onNativeExpressAdLoad(ads: MutableList<TTNativeExpressAd>?) {
                val ad = ads?.firstOrNull()
                if (ad == null) {
                    onLoaded(false)
                } else {
                    render(ad)
                }
            }
        })
    }

    private fun render(ad: TTNativeExpressAd) {
        expressAd?.destroy()
        expressAd = ad
        ad.setExpressInteractionListener(
            object : TTNativeExpressAd.ExpressAdInteractionListener {
                override fun onAdClicked(view: View, type: Int) = Unit

                override fun onAdShow(view: View, type: Int) = Unit

                override fun onRenderFail(view: View, message: String?, code: Int) {
                    onLoaded(false)
                }

                override fun onRenderSuccess(view: View, width: Float, height: Float) {
                    if (destroyed) {
                        ad.destroy()
                        return
                    }
                    container.removeAllViews()
                    container.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                    onLoaded(true)
                }
            },
        )
        ad.render()
    }

    private companion object {
        const val BANNER_HEIGHT_DP = 50f
        const val RETRY_COUNT = 8
        const val RETRY_INTERVAL_MS = 500L
    }
}
