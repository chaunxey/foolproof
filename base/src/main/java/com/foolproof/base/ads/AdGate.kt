package com.foolproof.base.ads

import android.app.Activity

/** 需要做「成功使用 N 次后看广告」门控的功能。 */
enum class FeatureId {
    IMAGE_COMPRESSION,
    DUPLICATE_CLEANUP,
    VIDEO_COMPRESSION,
    VIDEO_MERGE,
    FILE_CONVERSION,
}

/**
 * 广告门控接口。
 *
 * feature 只依赖本接口；具体实现（Meta Audience Network）放在 `:app`。
 * 实现方必须遵守：**广告加载失败要放行**，不能把核心功能卡死。
 */
interface AdGate {

    /** 在真正开始一次使用前调用；需要广告时会全屏展示，结束后回调 [onAllow]。 */
    fun maybeGate(activity: Activity, feature: FeatureId, onAllow: () -> Unit)

    /** 一次成功使用后调用；每个功能独立计数。 */
    fun onSuccess(feature: FeatureId)
}

/** 进程级注入点：`:app` 启动时设置；未接入广告时保持 null，功能不受影响。 */
object AdGateProvider {
    @Volatile
    var gate: AdGate? = null
}

/**
 * 便捷入口：没有广告实现或拿不到 Activity 时直接执行，保证功能不被卡死。
 */
fun AdGate?.gateOrRun(activity: Activity?, feature: FeatureId, block: () -> Unit) {
    if (this == null || activity == null) {
        block()
    } else {
        maybeGate(activity, feature, block)
    }
}
