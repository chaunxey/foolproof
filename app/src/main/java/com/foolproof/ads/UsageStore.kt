package com.foolproof.ads

import android.content.Context
import com.foolproof.ads.api.AdUsage
import com.foolproof.ads.api.Quota
import com.foolproof.base.ads.AdGateState
import com.foolproof.base.ads.FeatureId
import com.foolproof.common.storage.LocalStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 每个功能的两套额度：
 * - 每日免费 5 次（本地时区 0 点重置、不累计）；
 * - 激励余额（看激励视频 +5、可叠加、跨天不清零）。
 *
 * 内存缓存 + DataStore 异步落盘；变更后同步 [AdGateState] 供 UI 观察。
 */
object UsageStore : AdUsage {

    private val quotas = mutableMapOf<FeatureId, Quota>()
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        LocalStore.init(context.applicationContext)
        val today = today()
        FeatureId.entries.forEach { feature ->
            val stored = Quota(
                dayKey = LocalStore.readString(dayKey(feature), today),
                freeUsed = LocalStore.readInt(usedKey(feature), 0),
                bonus = LocalStore.readInt(bonusKey(feature), 0),
            )
            val quota = stored.rollTo(today)
            quotas[feature] = quota
            persist(feature, quota)
            AdGateState.update(feature, quota.available)
            // 旧版「成功次数」键不再使用，升级时清掉
            LocalStore.remove("uses_${feature.name}")
        }
        initialized = true
    }

    /** App 回到前台时刷新跨天额度。 */
    fun refreshAll() {
        if (!initialized) return
        val today = today()
        FeatureId.entries.forEach { feature ->
            val current = quotas[feature] ?: Quota(today)
            val rolled = current.rollTo(today)
            if (rolled != current) {
                quotas[feature] = rolled
                persist(feature, rolled)
            }
            AdGateState.update(feature, rolled.available)
        }
    }

    override fun quota(feature: FeatureId): Quota {
        val today = today()
        val current = quotas[feature] ?: Quota(today)
        val rolled = current.rollTo(today)
        if (rolled != current) {
            quotas[feature] = rolled
            persist(feature, rolled)
            AdGateState.update(feature, rolled.available)
        }
        return rolled
    }

    override fun consume(feature: FeatureId) {
        update(feature, quota(feature).consume())
    }

    override fun grantBonus(feature: FeatureId, amount: Int) {
        update(feature, quota(feature).grant(amount))
    }

    private fun update(feature: FeatureId, quota: Quota) {
        quotas[feature] = quota
        persist(feature, quota)
        AdGateState.update(feature, quota.available)
    }

    private fun persist(feature: FeatureId, quota: Quota) {
        LocalStore.writeString(dayKey(feature), quota.dayKey)
        LocalStore.writeInt(usedKey(feature), quota.freeUsed)
        LocalStore.writeInt(bonusKey(feature), quota.bonus)
    }

    private fun today(): String = dayFormat.format(Date())

    private fun dayKey(feature: FeatureId): String = "free_day_${feature.name}"

    private fun usedKey(feature: FeatureId): String = "free_used_${feature.name}"

    private fun bonusKey(feature: FeatureId): String = "bonus_${feature.name}"
}
