package com.foolproof.base.ads

import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 有额度门控的功能。 */
enum class FeatureId {
    IMAGE_COMPRESSION,
    DUPLICATE_CLEANUP,
    VIDEO_COMPRESSION,
    VIDEO_MERGE,
    FILE_CONVERSION,
}

/** 每个功能每天免费使用次数。 */
const val DAILY_FREE_USES = 5

/** 每看完一次激励视频增加的次数。 */
const val REWARD_AMOUNT = 5

/**
 * 广告额度门控。
 *
 * 额度由 `:app` 的 `UsageStore` 维护：每个功能每天免费 5 次 + 激励视频解锁的余额。
 * feature 只读状态、主动解锁；具体实现放在 `:ads:meta` / `:ads:pangle`。
 * 实现方必须遵守：**广告不可用时要放行**，不能把核心功能卡死。
 */
interface AdGate {

    /** 一次成功使用后调用，扣 1 次额度（先每日免费、再激励余额）。 */
    fun onSuccess(feature: FeatureId)

    /** 当前可用次数（今日免费剩余 + 激励余额）。 */
    fun available(feature: FeatureId): Int

    fun isLocked(feature: FeatureId): Boolean = available(feature) <= 0

    /** 用户主动看激励视频解锁；true = 可以执行本次操作。 */
    fun requestUnlock(activity: Activity, feature: FeatureId, onResult: (Boolean) -> Unit)
}

/** 进程级注入点：`:app` 启动时设置；未接入广告时保持 null，功能不受影响。 */
object AdGateProvider {
    @Volatile
    var gate: AdGate? = null
}

/** 供 Compose 观察每个功能的可用次数；由 `:app` 的 `UsageStore` 写入。 */
object AdGateState {

    private val _available = MutableStateFlow<Map<FeatureId, Int>>(emptyMap())
    val available: StateFlow<Map<FeatureId, Int>> = _available.asStateFlow()

    fun update(feature: FeatureId, remaining: Int) {
        _available.update { it + (feature to remaining) }
    }

    fun clear() {
        _available.value = emptyMap()
    }
}
