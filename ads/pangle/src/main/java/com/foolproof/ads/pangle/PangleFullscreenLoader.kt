package com.foolproof.ads.pangle

import android.app.Activity
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.TTAdConstant
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTFullScreenVideoAd
import com.foolproof.ads.api.FullscreenAd
import com.foolproof.ads.api.FullscreenAdLoader

/** 穿山甲全屏视频：关闭/出错都会收口到 CountingAdGate。 */
internal class PangleFullscreenLoader(private val activity: Activity) : FullscreenAdLoader {

    override val isConfigured: Boolean
        get() = PangleAdsConfig.configured

    private var closeCallback: (() -> Unit)? = null

    override fun load(onLoaded: (FullscreenAd) -> Unit, onFailed: () -> Unit) {
        val adNative = runCatching { TTAdSdk.getAdManager().createAdNative(activity) }.getOrNull()
        if (adNative == null) {
            onFailed()
            return
        }
        val adSlot = AdSlot.Builder()
            .setCodeId(PangleAdsConfig.INTERSTITIAL_CODE_ID)
            .setOrientation(TTAdConstant.VERTICAL)
            .build()
        runCatching {
            adNative.loadFullScreenVideoAd(
                adSlot,
                object : TTAdNative.FullScreenVideoAdListener {
                    override fun onError(code: Int, message: String?) {
                        onFailed()
                    }

                    override fun onFullScreenVideoAdLoad(ad: TTFullScreenVideoAd) {
                        ad.setFullScreenVideoAdInteractionListener(
                            object : TTFullScreenVideoAd.FullScreenVideoAdInteractionListener {
                                override fun onAdShow() = Unit

                                override fun onAdVideoBarClick() = Unit

                                override fun onAdClose() {
                                    closeCallback?.invoke()
                                }

                                override fun onVideoComplete() = Unit

                                override fun onSkippedVideo() = Unit
                            },
                        )
                        onLoaded(
                            object : FullscreenAd {
                                override fun show(onClosed: () -> Unit) {
                                    closeCallback = onClosed
                                    runCatching { ad.showFullScreenVideoAd(activity) }
                                        .onFailure { closeCallback?.invoke() }
                                }

                                override fun destroy() {
                                    closeCallback = null
                                }
                            },
                        )
                    }

                    override fun onFullScreenVideoCached() = Unit

                    override fun onFullScreenVideoCached(ad: TTFullScreenVideoAd) = Unit
                },
            )
        }.onFailure { onFailed() }
    }
}
