package com.foolproof.ads.meta

import android.content.Context
import android.view.View
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.AdListener
import com.facebook.ads.AdSize
import com.facebook.ads.AdView
import com.foolproof.ads.api.AdBannerSlot

/** Meta 横幅：320×50，60 秒刷新由调用方驱动。 */
internal class MetaBannerSlot(
    context: Context,
    private val onLoaded: (Boolean) -> Unit,
) : AdBannerSlot {

    private val adView = AdView(context, MetaAdsConfig.BANNER_PLACEMENT_ID, AdSize.BANNER_HEIGHT_50)
    private var destroyed = false

    override val view: View
        get() = adView

    init {
        val listener = object : AdListener {
            override fun onError(ad: Ad, error: AdError) {
                onLoaded(false)
            }

            override fun onAdLoaded(ad: Ad) {
                onLoaded(true)
            }

            override fun onAdClicked(ad: Ad) = Unit

            override fun onLoggingImpression(ad: Ad) = Unit
        }
        adView.loadAd(adView.buildLoadAdConfig().withAdListener(listener).build())
    }

    override fun refresh() {
        if (destroyed) return
        adView.loadAd()
    }

    override fun destroy() {
        if (destroyed) return
        destroyed = true
        adView.destroy()
    }
}
