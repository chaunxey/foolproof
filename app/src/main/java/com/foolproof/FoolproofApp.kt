package com.foolproof

import com.foolproof.base.BaseApplication
import com.foolproof.features.compression.di.imageCompressionModule
import com.foolproof.features.duplicates.di.duplicateCleanupModule
import com.foolproof.features.video.di.videoCompressionModule
import org.koin.core.module.Module

/**
 * 应用入口。base 模块负责启动 Koin，这里追加各 feature 模块。
 */
class FoolproofApp : BaseApplication() {

    override fun koinModules(): List<Module> =
        super.koinModules() + imageCompressionModule + duplicateCleanupModule + videoCompressionModule
}
