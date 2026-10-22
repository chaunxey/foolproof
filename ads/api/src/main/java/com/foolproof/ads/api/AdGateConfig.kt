package com.foolproof.ads.api

/** 门控参数：N 次成功使用后看一次广告；超过 [timeoutMs] 没有结果就放行。 */
data class AdGateConfig(
    val usesBeforeAd: Int = 5,
    val timeoutMs: Long = 5_000L,
)
