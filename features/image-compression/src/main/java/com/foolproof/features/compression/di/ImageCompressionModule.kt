package com.foolproof.features.compression.di

import android.os.Build
import com.foolproof.features.compression.CompressionViewModel
import com.foolproof.features.compression.data.AndroidImageCompressor
import com.foolproof.features.compression.data.ContentResolverImageMetadataReader
import com.foolproof.features.compression.data.MediaStoreResultExporter
import com.foolproof.features.compression.domain.ImageCompressor
import com.foolproof.features.compression.domain.ImageMetadataReader
import com.foolproof.features.compression.domain.ResultExporter
import com.foolproof.features.compression.platform.PlatformCapabilities
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * 图片压缩模块的依赖声明（宪法 §5）。
 * `:app` 的 `FoolproofApp.koinModules()` 会把它追加到 base 模块之后。
 */
val imageCompressionModule = module {

    single { PlatformCapabilities(Build.VERSION.SDK_INT) }

    single<ImageCompressor> { AndroidImageCompressor(get(), get()) }

    single<ImageMetadataReader> { ContentResolverImageMetadataReader(get(), get()) }

    single<ResultExporter> { MediaStoreResultExporter(get(), get()) }

    viewModel { CompressionViewModel(get(), get(), get(), get(), get()) }
}
