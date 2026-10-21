package com.foolproof

import com.foolproof.base.BaseApplication
import com.foolproof.features.compression.di.imageCompressionModule
import com.foolproof.features.conversion.data.PdfOutputCleaner
import com.foolproof.features.conversion.di.fileConversionModule
import com.foolproof.features.duplicates.di.duplicateCleanupModule
import com.foolproof.features.video.data.VideoOutputCleaner
import com.foolproof.features.video.di.videoCompressionModule
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
        // 启动清理上个进程可能残留的半成品（FR-014：不得留下孤立文件）。
        // 放后台线程：只删几个文件，但不能占用冷启动的关键路径。
        thread(name = "output-cleanup", isDaemon = true) {
            runCatching { GlobalContext.get().get<VideoOutputCleaner>().cleanAll() }
            runCatching { GlobalContext.get().get<PdfOutputCleaner>().cleanAll() }
        }
    }
}
