package com.foolproof.ads.api

/** 门控参数：激励视频加载超时与失败冷却。 */
data class AdGateConfig(
    val loadTimeoutMs: Long = 8_000L,
    val failCooldownMs: Long = 60_000L,
)
