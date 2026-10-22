package com.foolproof.ads.meta

import android.app.Activity
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.InterstitialAd
import com.facebook.ads.InterstitialAdListener
import com.foolproof.ads.api.FullscreenAd
import com.foolproof.ads.api.FullscreenAdLoader

/** Meta 插屏：加载失败放行，关闭后由 [com.foolproof.ads.api.CountingAdGate] 清零并放行。 */
internal class MetaFullscreenLoader(private val activity: Activity) : FullscreenAdLoader {

    override val isConfigured: Boolean
        get() = MetaAdsConfig.configured

    private var closeCallback: (() -> Unit)? = null

    override fun load(onLoaded: (FullscreenAd) -> Unit, onFailed: () -> Unit) {
        val interstitial = InterstitialAd(activity, MetaAdsConfig.INTERSTITIAL_PLACEMENT_ID)
        val listener = object : InterstitialAdListener {
            override fun onError(ad: Ad, error: AdError) {
                ad.destroy()
                onFailed()
            }

            override fun onAdLoaded(ad: Ad) {
                onLoaded(
                    object : FullscreenAd {
                        override fun show(onClosed: () -> Unit) {
                            closeCallback = onClosed
                            interstitial.show()
                        }

                        override fun destroy() {
                            interstitial.destroy()
                        }
                    },
                )
            }

            override fun onInterstitialDismissed(ad: Ad) {
                closeCallback?.invoke()
            }

            override fun onInterstitialDisplayed(ad: Ad) = Unit

            override fun onAdClicked(ad: Ad) = Unit

            override fun onLoggingImpression(ad: Ad) = Unit
        }
        interstitial.loadAd(interstitial.buildLoadAdConfig().withAdListener(listener).build())
    }
}
