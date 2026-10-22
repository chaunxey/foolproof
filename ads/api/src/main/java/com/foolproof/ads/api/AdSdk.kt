package com.foolproof.ads.api

import android.app.Application
import android.content.Context
import android.view.View
import com.foolproof.base.ads.AdGate

/** 广告 provider 标识；构建期由 `foolproof.adSdk` 决定，运行期不可切换。 */
enum class AdProviderId { META, PANGLE }

/** 横幅槽：SDK 提供原生 View，刷新与释放由实现负责。 */
interface AdBannerSlot {
    val view: View

    /** 重新请求一次（调用方按 60 秒节流）。 */
    fun refresh()

    /** 页面销毁时调用；实现必须幂等。 */
    fun destroy()
}

/** 原生开屏槽；不支持原生开屏的 provider 不使用该类型。 */
interface SplashAdSlot {
    val view: View

    /** SDK 的跳过/倒计时结束回调，由调用方关闭广告层。 */
    fun setOnCloseListener(listener: () -> Unit)

    /** 广告层关闭后调用；实现必须幂等。 */
    fun destroy()
}

/**
 * 广告能力入口。
 *
 * 继承 `:base` 的 [AdGate]，`:app` 直接把实例交给 `AdGateProvider`，
 * feature 模块（图片/去重/视频/转换）不需要任何改动。
 */
interface AdSdk : AdGate {
    val id: AdProviderId

    /** appId + 代码位是否齐全；false 时一律降级：不初始化、不请求、占位、门控放行。 */
    val configured: Boolean

    fun init(app: Application, debug: Boolean)

    /** 未配置返回 null；加载结果通过 [onLoaded] 回调（false 表示失败，界面显示占位）。 */
    fun createBanner(context: Context, onLoaded: (Boolean) -> Unit): AdBannerSlot?

    /** 拉取原生开屏；不支持、失败或未配置时回调 [onUnavailable]。 */
    fun loadSplash(
        context: Context,
        onReady: (SplashAdSlot) -> Unit,
        onUnavailable: () -> Unit,
    )
}

/** 进程级当前 provider：`:app` 启动时写入，Compose 侧只读。 */
object AdSdkRegistry {
    @Volatile
    var sdk: AdSdk? = null
}
