package com.foolproof.ads.api

import com.foolproof.base.ads.DAILY_FREE_USES
import com.foolproof.base.ads.FeatureId
import com.foolproof.base.ads.REWARD_AMOUNT

/**
 * 两套独立额度：
 * - 每日免费：`DAILY_FREE_USES` 次/天，0 点重置、不累计；
 * - 激励余额：看激励视频累加，可重复、跨天不清零。
 *
 * 纯 Kotlin，可直接 JVM 单测。
 */
data class Quota(
    val dayKey: String,
    val freeUsed: Int = 0,
    val bonus: Int = 0,
) {

    val freeRemaining: Int
        get() = (DAILY_FREE_USES - freeUsed).coerceAtLeast(0)

    val available: Int
        get() = freeRemaining + bonus

    /** 跨天时只重置每日免费，激励余额保留。 */
    fun rollTo(today: String): Quota =
        if (dayKey == today) this else copy(dayKey = today, freeUsed = 0)

    /** 成功使用一次：先扣免费，再扣激励。 */
    fun consume(): Quota =
        if (freeRemaining > 0) {
            copy(freeUsed = (freeUsed + 1).coerceAtMost(DAILY_FREE_USES))
        } else {
            copy(bonus = (bonus - 1).coerceAtLeast(0))
        }

    fun grant(amount: Int = REWARD_AMOUNT): Quota =
        copy(bonus = (bonus + amount).coerceAtLeast(0))
}

/** 额度读写：`:app` 的 `UsageStore` 实现（DataStore 持久化）。 */
interface AdUsage {

    fun quota(feature: FeatureId): Quota

    fun consume(feature: FeatureId)

    fun grantBonus(feature: FeatureId, amount: Int = REWARD_AMOUNT)
}
