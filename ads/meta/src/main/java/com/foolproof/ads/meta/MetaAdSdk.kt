package com.foolproof.ads.meta

import android.app.Activity
import android.app.Application
import android.content.Context
import com.facebook.ads.AdSettings
import com.facebook.ads.AudienceNetworkAds
import com.foolproof.ads.api.AdBannerSlot
import com.foolproof.ads.api.AdGateConfig
import com.foolproof.ads.api.AdProviderId
import com.foolproof.ads.api.AdSdk
import com.foolproof.ads.api.AdUsage
import com.foolproof.ads.api.HandlerDelayScheduler
import com.foolproof.ads.api.RewardedGate
import com.foolproof.ads.api.SplashAdSlot
import com.foolproof.base.ads.FeatureId

/**
 * Meta Audience Network 实现。
 *
 * 横幅/激励解锁/开屏：开屏继续用应用自绘壳，因此 [loadSplash] 永远回调 `onUnavailable`。
 */
class MetaAdSdk(private val usage: AdUsage) : AdSdk {

    override val id: AdProviderId = AdProviderId.META

    override val configured: Boolean
        get() = MetaAdsConfig.configured

    private val gate = RewardedGate(
        usage = usage,
        config = AdGateConfig(),
        loaderFactory = { activity -> MetaRewardedLoader(activity) },
        schedulerFactory = { HandlerDelayScheduler() },
    )

    override fun init(app: Application, debug: Boolean) {
        if (!configured && MetaAdsConfig.REWARDED_PLACEMENT_ID.isBlank()) return
        runCatching {
            // debug 包走 Meta 测试广告；release 包不要开
            AdSettings.setDebugBuild(debug)
            AudienceNetworkAds.initialize(app)
        }
    }

    override fun createBanner(context: Context, onLoaded: (Boolean) -> Unit): AdBannerSlot? =
        if (configured) MetaBannerSlot(context, onLoaded) else null

    override fun loadSplash(
        context: Context,
        onReady: (SplashAdSlot) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        onUnavailable()
    }

    override fun available(feature: FeatureId): Int = gate.available(feature)

    override fun requestUnlock(
        activity: Activity,
        feature: FeatureId,
        onResult: (Boolean) -> Unit,
    ) {
        gate.requestUnlock(activity, feature, onResult)
    }

    override fun onSuccess(feature: FeatureId) {
        gate.onSuccess(feature)
    }
}
