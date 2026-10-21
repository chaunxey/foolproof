package com.foolproof.ads

import android.app.Activity
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.InterstitialAd
import com.facebook.ads.InterstitialAdListener
import com.foolproof.base.ads.AdGate
import com.foolproof.base.ads.FeatureId

/**
 * Meta Audience Network 的广告门控实现。
 *
 * 规则：同一功能成功使用 5 次后，下一次使用前展示插屏；关闭后计数清零放行。
 * 广告加载失败也放行（宁可不赚这次，也不能让用户用不了功能）。
 */
class MetaAdGate : AdGate {

    private var interstitial: InterstitialAd? = null

    override fun maybeGate(activity: Activity, feature: FeatureId, onAllow: () -> Unit) {
        if (UsageStore.uses(feature) < AdsConfig.USES_BEFORE_AD) {
            onAllow()
            return
        }
        if (!AdsConfig.configured) {
            // 还没配 placement ID：直接放行，避免开发期卡死
            UsageStore.reset(feature)
            onAllow()
            return
        }

        val ad = InterstitialAd(activity, AdsConfig.INTERSTITIAL_PLACEMENT_ID)
        interstitial = ad
        val listener = object : InterstitialAdListener {
            override fun onError(ad: Ad, error: AdError) {
                finish(feature, onAllow)
            }

            override fun onAdLoaded(ad: Ad) {
                interstitial?.show()
            }

            override fun onInterstitialDismissed(ad: Ad) {
                finish(feature, onAllow)
            }

            override fun onInterstitialDisplayed(ad: Ad) = Unit

            override fun onAdClicked(ad: Ad) = Unit

            override fun onLoggingImpression(ad: Ad) = Unit
        }
        ad.loadAd(ad.buildLoadAdConfig().withAdListener(listener).build())
    }

    override fun onSuccess(feature: FeatureId) {
        UsageStore.increment(feature)
    }

    private fun finish(feature: FeatureId, onAllow: () -> Unit) {
        interstitial?.destroy()
        interstitial = null
        UsageStore.reset(feature)
        onAllow()
    }
}
