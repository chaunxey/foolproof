package com.foolproof.ads.api

import android.app.Activity
import com.foolproof.base.ads.AdGate
import com.foolproof.base.ads.FeatureId
import java.util.concurrent.atomic.AtomicBoolean

/** 一个已加载好的激励视频。 */
interface RewardedAd {
    /** 展示；[onFinished] 只在关闭时回调一次，`rewarded` 表示是否拿到奖励。 */
    fun show(onFinished: (rewarded: Boolean) -> Unit)

    fun destroy()
}

/** 负责加载激励视频；实现里捕获当前 Activity 与 SDK 对象。 */
interface RewardedAdLoader {
    val isConfigured: Boolean

    fun load(onLoaded: (RewardedAd) -> Unit, onFailed: () -> Unit)
}

/**
 * 与 SDK 无关的额度门控策略。
 *
 * 规则：
 * 1. 还有可用次数 → 直接放行；
 * 2. 未配置广告 → 放行（不扣额度）；
 * 3. 失败冷却内 → 放行，避免反复等待；
 * 4. 否则加载并展示激励视频：
 *    - 奖励成功 → 加 5 次余额 + 放行；
 *    - 中途关闭/跳过 → 不放行、不加次数；
 *    - 加载失败/超时 → 放行当前一次（不扣额度），并进入 60 秒冷却；
 * 5. `onResult` 只回调一次。
 */
class RewardedGate(
    private val usage: AdUsage,
    private val config: AdGateConfig = AdGateConfig(),
    private val loaderFactory: (Activity) -> RewardedAdLoader,
    private val schedulerFactory: () -> DelayScheduler,
    private val clock: () -> Long = System::currentTimeMillis,
) : AdGate {

    private val lastFailureAt = mutableMapOf<FeatureId, Long>()

    override fun onSuccess(feature: FeatureId) {
        usage.consume(feature)
    }

    override fun available(feature: FeatureId): Int = usage.quota(feature).available

    override fun requestUnlock(
        activity: Activity,
        feature: FeatureId,
        onResult: (Boolean) -> Unit,
    ) {
        unlock(feature, loaderFactory(activity), onResult)
    }

    /** 不依赖 Activity 的核心流程，便于 JVM 单测。 */
    internal fun unlock(
        feature: FeatureId,
        loader: RewardedAdLoader,
        onResult: (Boolean) -> Unit,
    ) {        if (usage.quota(feature).available > 0) {
            onResult(true)
            return
        }
        if (!loader.isConfigured) {
            onResult(true)
            return
        }
        val lastFailure = lastFailureAt[feature]
        if (lastFailure != null && clock() - lastFailure < config.failCooldownMs) {
            onResult(true)
            return
        }

        val done = AtomicBoolean(false)
        var shownAd: RewardedAd? = null
        val scheduler = schedulerFactory()

        fun finish(allowed: Boolean) {
            if (!done.compareAndSet(false, true)) return
            scheduler.cancel()
            shownAd?.destroy()
            shownAd = null
            onResult(allowed)
        }

        fun failOpen() {
            lastFailureAt[feature] = clock()
            finish(true)
        }

        scheduler.postDelayed(config.loadTimeoutMs) { failOpen() }
        runCatching {
            loader.load(
                onLoaded = { ad ->
                    if (done.get()) {
                        ad.destroy()
                    } else {
                        // 加载完成，取消「加载超时」；展示阶段以 SDK 的关闭回调为准
                        scheduler.cancel()
                        shownAd = ad
                        ad.show { rewarded ->
                            if (done.get()) {
                                return@show
                            }
                            if (rewarded) {
                                usage.grantBonus(feature)
                            }
                            finish(rewarded)
                        }
                    }
                },
                onFailed = { failOpen() },
            )
        }.onFailure { failOpen() }
    }
}
