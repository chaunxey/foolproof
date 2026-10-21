package com.foolproof.features.conversion.di

import android.content.Context
import com.foolproof.features.conversion.FileConversionViewModel
import com.foolproof.features.conversion.data.BitmapImageLoader
import com.foolproof.features.conversion.data.ContentUriTextLoader
import com.foolproof.features.conversion.data.MediaStorePdfExporter
import com.foolproof.features.conversion.data.NativePdfGenerator
import com.foolproof.features.conversion.data.PdfOutputCleaner
import com.foolproof.features.conversion.domain.PdfConverter
import com.foolproof.features.conversion.domain.PdfImageLoader
import com.foolproof.features.conversion.domain.PdfTextLoader
import java.io.File
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

val fileConversionModule: Module = module {

    single<PdfImageLoader> { BitmapImageLoader(get(), get()) }
    single<PdfTextLoader> { ContentUriTextLoader(get(), get()) }
    single<PdfConverter> { NativePdfGenerator(get(), get(), get()) }

    single<com.foolproof.features.conversion.domain.PdfExporter> { MediaStorePdfExporter(get(), get()) }
    single { PdfOutputCleaner(File(get<Context>().filesDir, "pdf"), get()) }

    viewModel {
        FileConversionViewModel(
            converter = get(),
            imageLoader = get(),
            exporter = get(),
            outputDir = File(get<Context>().filesDir, "pdf"),
            log = get(),
        )
    }
}
