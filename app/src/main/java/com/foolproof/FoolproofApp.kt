package com.foolproof

import android.content.pm.ApplicationInfo
import com.foolproof.ads.SplashScheduler
import com.foolproof.ads.UsageStore
import com.foolproof.ads.api.AdSdkRegistry
import com.foolproof.ads.createAdSdk
import com.foolproof.base.BaseApplication
import com.foolproof.base.ads.AdGateProvider
import com.foolproof.features.compression.di.imageCompressionModule
import com.foolproof.features.conversion.data.PdfOutputCleaner
import com.foolproof.features.conversion.di.fileConversionModule
import com.foolproof.features.duplicates.di.duplicateCleanupModule
import com.foolproof.features.video.data.VideoOutputCleaner
import com.foolproof.features.video.di.videoCompressionModule
import com.foolproof.ui.auth.AuthStore
import org.koin.core.context.GlobalContext
import org.koin.core.module.Module
import kotlin.concurrent.thread

/**
 * 应用入口。base 模块负责启动 Koin，这里追加各 feature 模块。
 */
class FoolproofApp : BaseApplication() {

    override fun koinModules(): List<Module> =
        super.koinModules() +
            imageCompressionModule +
            duplicateCleanupModule +
            videoCompressionModule +
            fileConversionModule

    override fun onCreate() {
        super.onCreate()
        clearLegacyPreferences()
        AuthStore.init(this)
        setupAds()
        // 启动清理上个进程可能残留的半成品（FR-014：不得留下孤立文件）。
        // 放后台线程：只删几个文件，但不能占用冷启动的关键路径。
        thread(name = "output-cleanup", isDaemon = true) {
            runCatching { GlobalContext.get().get<VideoOutputCleaner>().cleanAll() }
            runCatching { GlobalContext.get().get<PdfOutputCleaner>().cleanAll() }
        }
    }

    /** 旧版 SharedPreferences 数据不再保留，升级后直接删除。 */
    private fun clearLegacyPreferences() {
        listOf("app_settings", "auth", "ad_usage", "ad_splash").forEach {
            deleteSharedPreferences(it)
        }
    }

    /**
     * 广告初始化：provider 由构建开关 `foolproof.adSdk` 决定（默认 Meta）。
     * feature 模块只依赖 `:base` 的 `AdGate`，这里把同一个实例交给 AdGateProvider。
     */
    private fun setupAds() {
        UsageStore.init(this)
        SplashScheduler.init(this)

        val sdk = createAdSdk(UsageStore)
        AdSdkRegistry.sdk = sdk
        AdGateProvider.gate = sdk

        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // SDK 初始化失败不能让应用起不来：广告降级，核心功能照常用。
        runCatching { sdk.init(this, debuggable) }
    }
}
