package com.foolproof.ads.api

import com.foolproof.base.ads.FeatureId

/** 成功使用次数存储；`:app` 的 `UsageStore` 实现（DataStore 持久化）。 */
interface AdUsage {
    fun uses(feature: FeatureId): Int

    fun increment(feature: FeatureId)

    fun reset(feature: FeatureId)
}
