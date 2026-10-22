package com.foolproof.ads

import android.content.Context
import com.foolproof.base.ads.FeatureId
import com.foolproof.common.storage.LocalStore

/** 每个功能「成功使用次数」的持久化计数，内存缓存 + DataStore 异步落盘。 */
object UsageStore {

    private val counts = mutableMapOf<FeatureId, Int>()

    fun init(context: Context) {
        LocalStore.init(context.applicationContext)
        FeatureId.entries.forEach { feature ->
            counts[feature] = LocalStore.readInt(key(feature), 0)
        }
    }

    fun uses(feature: FeatureId): Int = counts[feature] ?: 0

    fun increment(feature: FeatureId) {
        val next = uses(feature) + 1
        counts[feature] = next
        LocalStore.writeInt(key(feature), next)
    }

    fun reset(feature: FeatureId) {
        counts[feature] = 0
        LocalStore.remove(key(feature))
    }

    private fun key(feature: FeatureId): String = "uses_${feature.name}"
}
