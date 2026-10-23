package com.foolproof.ads.pangle

import android.app.Activity
import android.app.Application
import android.content.Context
import com.bytedance.sdk.openadsdk.AdSlot
import com.bytedance.sdk.openadsdk.CSJAdError
import com.bytedance.sdk.openadsdk.CSJSplashAd
import com.bytedance.sdk.openadsdk.TTAdConfig
import com.bytedance.sdk.openadsdk.TTAdNative
import com.bytedance.sdk.openadsdk.TTAdSdk
import com.bytedance.sdk.openadsdk.mediation.ad.MediationAdSlot
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
 * 穿山甲（GroMore 融合 SDK）实现，走官方 Maven `com.pangle.cn:mediation-sdk`。
 *
 * 广告位是 GroMore 聚合广告位（ID 以 1 开头），因此初始化必须打开 `useMediation`，
 * 每个请求都要带 `MediationAdSlot`。
 *
 * - 初始化：`TTAdSdk.init` + `TTAdSdk.start`，默认关闭位置/电话/应用列表采集；
 * - 横幅：模板横幅，未就绪时短轮询等 SDK 初始化完成；
 * - 门控：插全屏/全屏视频，关闭/出错收口到 `CountingAdGate`；
 * - 开屏：`loadSplashAd` 渲染成功后交给 `:app` 的广告层；启动时预热一次。
 */
class PangleAdSdk(private val usage: AdUsage) : AdSdk {

    override val id: AdProviderId = AdProviderId.PANGLE

    override val configured: Boolean
        get() = PangleAdsConfig.configured

    private val gate = CountingAdGate(usage, AdGateConfig()) { HandlerDelayScheduler() }

    @Volatile
    private var splashAd: CSJSplashAd? = null

    @Volatile
    private var splashLoadedAt = 0L

    @Volatile
    private var splashLoading = false

    override fun init(app: Application, debug: Boolean) {
        if (!configured) return
        val appName = runCatching {
            app.applicationInfo.loadLabel(app.packageManager).toString()
        }.getOrDefault("Foolproof")
        val initialized = runCatching {
            TTAdSdk.init(
                app,
                TTAdConfig.Builder()
                    .appId(PangleAdsConfig.APP_ID)
                    .appName(appName)
                    .debug(debug)
                    .allowShowNotify(true)
                    .supportMultiProcess(false)
                    // GroMore 聚合广告位（1 开头）必须打开聚合开关
                    .useMediation(true)
                    .customController(PanglePrivacyController)
                    .build(),
            )
        }.getOrDefault(false)
        if (!initialized) return

        runCatching {
            TTAdSdk.start(
                object : TTAdSdk.Callback {
                    override fun success() {
                        preloadSplash(app)
                    }

                    override fun fail(code: Int, message: String?) = Unit
                },
            )
        }
    }

    override fun createBanner(context: Context, onLoaded: (Boolean) -> Unit): AdBannerSlot? =
        if (configured) PangleBannerSlot(context, onLoaded) else null

    override fun loadSplash(
        context: Context,
        onReady: (SplashAdSlot) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        if (!configured || PangleAdsConfig.SPLASH_CODE_ID.isBlank() || !TTAdSdk.isSdkReady()) {
            onUnavailable()
            return
        }
        val cached = splashAd
        if (cached != null && System.currentTimeMillis() - splashLoadedAt <= SPLASH_TTL_MS) {
            splashAd = null
            onReady(PangleSplashSlot(cached, context))
            return
        }
        requestSplash(context, cacheOnly = false, onReady = onReady, onUnavailable = onUnavailable)
    }

    override fun maybeGate(activity: Activity, feature: FeatureId, onAllow: () -> Unit) {
        gate.maybeGate(feature, onAllow, PangleFullscreenLoader(activity))
    }

    override fun onSuccess(feature: FeatureId) {
        gate.onSuccess(feature)
    }

    private fun preloadSplash(context: Context) {
        if (splashAd != null || splashLoading) return
        requestSplash(
            context.applicationContext,
            cacheOnly = true,
            onReady = {},
            onUnavailable = {},
        )
    }

    private fun requestSplash(
        context: Context,
        cacheOnly: Boolean,
        onReady: (SplashAdSlot) -> Unit,
        onUnavailable: () -> Unit,
    ) {
        val adNative = runCatching { TTAdSdk.getAdManager().createAdNative(context) }.getOrNull()
        if (adNative == null) {
            onUnavailable()
            return
        }
        splashLoading = true
        val dm = context.resources.displayMetrics
        val widthDp = (dm.widthPixels / dm.density).coerceAtLeast(320f)
        val heightDp = (dm.heightPixels / dm.density).coerceAtLeast(480f)
        val adSlot = AdSlot.Builder()
            .setCodeId(PangleAdsConfig.SPLASH_CODE_ID)
            .setExpressViewAcceptedSize(widthDp, heightDp)
            .setMediationAdSlot(MediationAdSlot.Builder().build())
            .build()
        runCatching {
            adNative.loadSplashAd(
                adSlot,
                object : TTAdNative.CSJSplashAdListener {
                    override fun onSplashLoadSuccess(ad: CSJSplashAd?) = Unit

                    override fun onSplashLoadFail(error: CSJAdError?) {
                        onUnavailable()
                    }

                    override fun onSplashRenderSuccess(ad: CSJSplashAd?) {
                        splashLoading = false
                        if (ad == null) {
                            onUnavailable()
                            return
                        }
                        if (cacheOnly) {
                            splashAd = ad
                            splashLoadedAt = System.currentTimeMillis()
                        } else {
                            onReady(PangleSplashSlot(ad, context))
                        }
                    }

                    override fun onSplashRenderFail(ad: CSJSplashAd?, error: CSJAdError?) {
                        onUnavailable()
                    }
                },
                SPLASH_LOAD_TIMEOUT_SECONDS,
            )
        }.onFailure {
            splashLoading = false
            onUnavailable()
        }
    }

    private companion object {
        const val SPLASH_LOAD_TIMEOUT_SECONDS = 3
        const val SPLASH_TTL_MS = 15 * 60 * 1000L
    }
}
