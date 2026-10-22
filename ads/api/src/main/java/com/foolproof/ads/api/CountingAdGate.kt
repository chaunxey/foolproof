package com.foolproof.ads.api

import com.foolproof.base.ads.FeatureId
import java.util.concurrent.atomic.AtomicBoolean

/** 一个已加载好的全屏广告（插屏 / 全屏视频）。 */
interface FullscreenAd {
    fun show(onClosed: () -> Unit)

    fun destroy()
}

/** 负责加载全屏广告；实现里可以捕获当前 Activity 与 SDK 对象。 */
interface FullscreenAdLoader {
    val isConfigured: Boolean

    fun load(onLoaded: (FullscreenAd) -> Unit, onFailed: () -> Unit)
}

/** 可注入的延时器（默认实现见 [HandlerDelayScheduler]），便于 JVM 单测。 */
interface DelayScheduler {
    fun postDelayed(delayMs: Long, block: () -> Unit)

    fun cancel()
}

/**
 * 与 SDK 无关的门控策略：计数、放行条件、超时兜底。
 *
 * 纯 Kotlin（不依赖 Android），可直接 JVM 单测。规则：
 * 1. 成功使用次数 < 阈值 → 放行；
 * 2. 未配置广告 → 清零 + 放行；
 * 3. 否则加载并展示；关闭 / 失败 / 超时（[AdGateConfig.timeoutMs]）→ 清零 + 放行；
 * 4. `onAllow` 只回调一次，迟到的关屏回调丢弃。
 */
class CountingAdGate(
    private val usage: AdUsage,
    private val config: AdGateConfig = AdGateConfig(),
    private val schedulerFactory: () -> DelayScheduler,
) {

    fun maybeGate(feature: FeatureId, onAllow: () -> Unit, loader: FullscreenAdLoader) {
        if (usage.uses(feature) < config.usesBeforeAd) {
            onAllow()
            return
        }
        if (!loader.isConfigured) {
            usage.reset(feature)
            onAllow()
            return
        }

        val done = AtomicBoolean(false)
        var shownAd: FullscreenAd? = null
        val scheduler = schedulerFactory()

        fun finish() {
            if (!done.compareAndSet(false, true)) return
            scheduler.cancel()
            shownAd?.destroy()
            shownAd = null
            usage.reset(feature)
            onAllow()
        }

        scheduler.postDelayed(config.timeoutMs) { finish() }
        runCatching {
            loader.load(
                onLoaded = { ad ->
                    if (done.get()) {
                        ad.destroy()
                    } else {
                        shownAd = ad
                        ad.show { finish() }
                    }
                },
                onFailed = { finish() },
            )
        }.onFailure { finish() }
    }

    fun onSuccess(feature: FeatureId) {
        usage.increment(feature)
    }
}
