package com.foolproof.features.video.di

import android.content.Context
import com.foolproof.features.video.VideoCompressionViewModel
import com.foolproof.features.video.data.ContentResolverVideoReader
import com.foolproof.features.video.data.Media3VideoCompressor
import com.foolproof.features.video.data.MediaCodecEncoderCatalog
import com.foolproof.features.video.data.MediaStoreVideoExporter
import com.foolproof.features.video.domain.EncoderCatalog
import com.foolproof.features.video.domain.VideoCompressor
import com.foolproof.features.video.domain.VideoExporter
import com.foolproof.features.video.domain.VideoReader
import java.io.File
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * 视频压缩的依赖声明（宪法 §5）。`:app` 会把本模块追加到 base 之后。
 */
val videoCompressionModule = module {

    single<VideoReader> { ContentResolverVideoReader(get(), get()) }

    single<EncoderCatalog> { MediaCodecEncoderCatalog(get()) }

    single<VideoCompressor> { Media3VideoCompressor(get(), get()) }

    single<VideoExporter> { MediaStoreVideoExporter(get(), get()) }

    viewModel {
        VideoCompressionViewModel(
            reader = get(),
            compressor = get(),
            encoderCatalog = get(),
            exporter = get(),
            outputDir = File(get<Context>().filesDir, "videos"),
            log = get(),
        )
    }
}
