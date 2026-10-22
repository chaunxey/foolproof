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
import com.foolproof.ads.api.CountingAdGate
import com.foolproof.ads.api.HandlerDelayScheduler
import com.foolproof.ads.api.SplashAdSlot
import com.foolproof.base.ads.FeatureId

/**
 * Meta Audience Network 实现。
 *
 * 横幅/门控/开屏都与 v1.1.0 一致：开屏继续用应用自绘壳，因此 [loadSplash]
 * 永远回调 `onUnavailable`。
 */
class MetaAdSdk(private val usage: AdUsage) : AdSdk {

    override val id: AdProviderId = AdProviderId.META

    override val configured: Boolean
        get() = MetaAdsConfig.configured

    private val gate = CountingAdGate(usage, AdGateConfig()) { HandlerDelayScheduler() }

    override fun init(app: Application, debug: Boolean) {
        if (!configured) return
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

    override fun maybeGate(activity: Activity, feature: FeatureId, onAllow: () -> Unit) {
        gate.maybeGate(feature, onAllow, MetaFullscreenLoader(activity))
    }

    override fun onSuccess(feature: FeatureId) {
        gate.onSuccess(feature)
    }
}
