package com.foolproof.features.duplicates.di

import com.foolproof.features.duplicates.DuplicateViewModel
import com.foolproof.features.duplicates.data.MediaStorePhotoDeleter
import com.foolproof.features.duplicates.data.MediaStorePhotoScanner
import com.foolproof.features.duplicates.data.ThumbnailPixelSource
import com.foolproof.features.duplicates.domain.PhotoDeleter
import com.foolproof.features.duplicates.domain.PhotoScanner
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * 重复照片清理的依赖声明（宪法 §5）。`:app` 会把本模块追加到 base 之后。
 */
val duplicateCleanupModule = module {

    single { ThumbnailPixelSource(get()) }

    single<PhotoScanner> { MediaStorePhotoScanner(get(), get(), get()) }

    single<PhotoDeleter> { MediaStorePhotoDeleter(get(), get()) }

    viewModel { DuplicateViewModel(get(), get(), get()) }
}
