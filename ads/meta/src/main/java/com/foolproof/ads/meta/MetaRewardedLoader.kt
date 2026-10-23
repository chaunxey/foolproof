package com.foolproof.ads.meta

import android.app.Activity
import com.facebook.ads.Ad
import com.facebook.ads.AdError
import com.facebook.ads.RewardedVideoAd
import com.facebook.ads.RewardedVideoAdListener
import com.foolproof.ads.api.RewardedAd
import com.foolproof.ads.api.RewardedAdLoader

/** Meta 激励视频：`onRewardedVideoCompleted` 才算拿到奖励。 */
internal class MetaRewardedLoader(private val activity: Activity) : RewardedAdLoader {

    override val isConfigured: Boolean
        get() = MetaAdsConfig.REWARDED_PLACEMENT_ID.isNotBlank()

    private var rewarded = false
    private var closeCallback: ((Boolean) -> Unit)? = null

    override fun load(onLoaded: (RewardedAd) -> Unit, onFailed: () -> Unit) {
        val rewardAd = RewardedVideoAd(activity, MetaAdsConfig.REWARDED_PLACEMENT_ID)
        val listener = object : RewardedVideoAdListener {
            override fun onError(ad: Ad, error: AdError) {
                ad.destroy()
                onFailed()
            }

            override fun onAdLoaded(ad: Ad) {
                onLoaded(
                    object : RewardedAd {
                        override fun show(onFinished: (Boolean) -> Unit) {
                            closeCallback = onFinished
                            rewarded = false
                            if (!runCatching { rewardAd.show() }.getOrDefault(false)) {
                                closeCallback?.invoke(false)
                                closeCallback = null
                            }
                        }

                        override fun destroy() {
                            closeCallback = null
                            rewardAd.destroy()
                        }
                    },
                )
            }

            override fun onRewardedVideoCompleted() {
                rewarded = true
            }

            override fun onRewardedVideoClosed() {
                closeCallback?.invoke(rewarded)
                closeCallback = null
                rewarded = false
            }

            override fun onAdClicked(ad: Ad) = Unit

            override fun onLoggingImpression(ad: Ad) = Unit
        }
        rewardAd.loadAd(rewardAd.buildLoadAdConfig().withAdListener(listener).build())
    }
}

