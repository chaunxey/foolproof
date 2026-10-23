package com.foolproof.ads.pangle

import android.app.Activity
import android.os.Bundle
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.TTAdConstant
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.TTRewardVideoAd
import com.bytedance.sdk.openadsdk.mediation.ad.MediationAdSlot
import com.foolproof.ads.api.RewardedAd
import com.foolproof.ads.api.RewardedAdLoader

/** 穿山甲激励视频：奖励回调成功才解锁。 */
internal class PangleRewardedLoader(private val activity: Activity) : RewardedAdLoader {

    override val isConfigured: Boolean
        get() = PangleAdsConfig.REWARDED_AD_ID.isNotBlank()

    private var rewarded = false
    private var closeCallback: ((Boolean) -> Unit)? = null

    override fun load(onLoaded: (RewardedAd) -> Unit, onFailed: () -> Unit) {
        val adNative = runCatching { TTAdSdk.getAdManager().createAdNative(activity) }.getOrNull()
        if (adNative == null) {
            onFailed()
            return
        }
        val adSlot = AdSlot.Builder()
            .setCodeId(PangleAdsConfig.REWARDED_AD_ID)
            .setOrientation(TTAdConstant.VERTICAL)
            .setMediationAdSlot(MediationAdSlot.Builder().build())
            .build()
        runCatching {
            adNative.loadRewardVideoAd(
                adSlot,
                object : TTAdNative.RewardVideoAdListener {
                    override fun onError(code: Int, message: String?) {
                        onFailed()
                    }

                    override fun onRewardVideoAdLoad(ad: TTRewardVideoAd?) {
                        if (ad == null) {
                            onFailed()
                            return
                        }
                        ad.setRewardAdInteractionListener(
                            object : TTRewardVideoAd.RewardAdInteractionListener {
                                override fun onAdShow() = Unit

                                override fun onAdVideoBarClick() = Unit

                                override fun onVideoComplete() = Unit

                                override fun onVideoError() = Unit

                                override fun onSkippedVideo() = Unit

                                override fun onRewardVerify(
                                    rewardVerify: Boolean,
                                    rewardAmount: Int,
                                    rewardName: String?,
                                    errorCode: Int,
                                    errorMsg: String?,
                                ) {
                                    if (rewardVerify) rewarded = true
                                }

                                override fun onRewardArrived(
                                    isRewardValid: Boolean,
                                    rewardType: Int,
                                    extraInfo: Bundle?,
                                ) {
                                    if (isRewardValid) rewarded = true
                                }

                                override fun onAdClose() {
                                    closeCallback?.invoke(rewarded)
                                    closeCallback = null
                                    rewarded = false
                                }
                            },
                        )
                        onLoaded(
                            object : RewardedAd {
                                override fun show(onFinished: (Boolean) -> Unit) {
                                    closeCallback = onFinished
                                    rewarded = false
                                    runCatching { ad.showRewardVideoAd(activity) }
                                        .onFailure {
                                            closeCallback?.invoke(false)
                                            closeCallback = null
                                        }
                                }

                                override fun destroy() {
                                    closeCallback = null
                                }
                            },
                        )
                    }

                    override fun onRewardVideoCached() = Unit

                    override fun onRewardVideoCached(ad: TTRewardVideoAd?) = Unit
                },
            )
        }.onFailure { onFailed() }
    }
}
