package com.foolproof.features.conversion

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.features.conversion.domain.PdfConvertRequest
import com.foolproof.features.conversion.domain.PdfConverter
import com.foolproof.features.conversion.domain.PdfFailure
import com.foolproof.features.conversion.domain.PdfImageLoader
import com.foolproof.features.conversion.domain.PdfPageSpec
import com.foolproof.features.conversion.domain.PdfProgress
import com.foolproof.features.conversion.domain.PdfSource
import com.foolproof.features.conversion.domain.PdfExporter
import java.io.File
import kotlinx.coroutines.Job

class FileConversionViewModel(
    private val converter: PdfConverter,
    private val imageLoader: PdfImageLoader,
    private val exporter: PdfExporter,
    private val outputDir: File,
    private val log: LogUtil,
) : BaseViewModel<FileConversionUiState, ConversionEffect>(
    FileConversionUiState(canSaveToDownloads = exporter.canSaveToDownloads()),
    log,
) {

    private var convertJob: Job? = null
    private var currentOutputPath: String? = null

    override fun onAction(action: UiAction) {
        when (action) {
            is ConversionAction.ModeChanged -> updateState {
                it.copy(
                    mode = action.mode,
                    phase = ConversionPhase.Idle,
                )
            }

            is ConversionAction.ImagesPicked -> onImagesPicked(action.uris)

            is ConversionAction.RemoveImage -> updateState { state ->
                val remaining = state.images.filterIndexed { index, _ -> index != action.index }
                state.copy(images = remaining, phase = ConversionPhase.Idle)
            }

            is ConversionAction.PerPageChanged -> updateState {
                it.copy(perPage = action.perPage.coerceIn(1, 4), phase = ConversionPhase.Idle)
            }

            is ConversionAction.MoveImage -> updateState { state ->
                val list = state.images.toMutableList()
                if (action.from in list.indices && action.to in list.indices) {
                    list.add(action.to, list.removeAt(action.from))
                }
                state.copy(images = list)
            }

            is ConversionAction.TextPicked -> onTextPicked(action)

            ConversionAction.Reset -> reset()

            ConversionAction.Start -> startConversion()

            ConversionAction.Cancel -> cancelConversion()

            ConversionAction.Share -> currentResult()?.let { sendEffect(ConversionEffect.Share(it.path)) }
                ?: sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.GENERATION_FAILED))

            ConversionAction.Open -> currentResult()?.let { sendEffect(ConversionEffect.Open(it.path)) }
                ?: sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.GENERATION_FAILED))

            ConversionAction.SaveToDownloads -> saveToDownloads()
        }
    }

    private fun onImagesPicked(uris: List<String>) {
        if (uris.isEmpty()) return
        launch {
            val current = currentState.images
            val room = PdfPageSpec.MAX_IMAGES - current.size
            if (room <= 0) {
                sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.TOO_MANY_IMAGES))
                return@launch
            }
            if (uris.size > room) {
                sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.TOO_MANY_IMAGES))
            }
            val added = uris.take(room).map { uri ->
                val size = imageLoader.readSize(uri)
                PdfSource.Image(
                    uri = uri,
                    width = size?.first ?: 0,
                    height = size?.second ?: 0,
                )
            }
            updateState {
                it.copy(
                    images = it.images + added,
                    phase = ConversionPhase.Ready,
                )
            }
        }
    }

    private fun onTextPicked(action: ConversionAction.TextPicked) {
        if (action.bytes > PdfPageSpec.MAX_TEXT_BYTES) {
            sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.TEXT_TOO_LARGE))
            return
        }
        updateState {
            it.copy(
                text = PdfSource.Text(action.uri, action.displayName, action.bytes),
                phase = ConversionPhase.Ready,
            )
        }
    }

    private fun startConversion() {
        val state = currentState
        if (!state.canConvert) return

        val request = when (state.mode) {
            ConversionMode.IMAGES -> PdfConvertRequest.FromImages(
                images = state.images,
                outputPath = nextOutputPath(),
                perPage = state.perPage,
            )

            ConversionMode.TEXT -> PdfConvertRequest.FromText(
                text = state.text ?: return,
                outputPath = nextOutputPath(),
            )
        }
        currentOutputPath = request.outputPath

        convertJob = launchJob {
            converter.convert(request).collect { progress ->
                when (progress) {
                    is PdfProgress.Running -> updateState {
                        it.copy(phase = ConversionPhase.Converting(progress.current, progress.total))
                    }

                    is PdfProgress.Success -> {
                        updateState { it.copy(phase = ConversionPhase.Done(progress.result)) }
                        sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.DONE))
                    }

                    is PdfProgress.Failed -> {
                        converter.cleanupPartialFile(request.outputPath)
                        updateState { it.copy(phase = ConversionPhase.Failed(progress.reason)) }
                        sendEffect(ConversionEffect.Message(progress.reason.toMessage()))
                    }

                    PdfProgress.Cancelled -> {
                        updateState { it.copy(phase = ConversionPhase.Ready) }
                        sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.CANCELLED))
                    }
                }
            }
        }
    }

    /** 重置：取消进行中的任务并回到初始状态；已生成的产物保留。 */
    private fun reset() {
        val wasConverting = currentState.isConverting
        val path = currentOutputPath
        convertJob?.cancel()
        convertJob = null
        launch {
            if (wasConverting) {
                converter.cancel()
                path?.let { converter.cleanupPartialFile(it) }
            }
            updateState { FileConversionUiState(canSaveToDownloads = it.canSaveToDownloads) }
        }
    }

    private fun cancelConversion() {
        val path = currentOutputPath
        convertJob?.cancel()
        convertJob = null
        launch {
            converter.cancel()
            path?.let { converter.cleanupPartialFile(it) }
            updateState { it.copy(phase = ConversionPhase.Ready) }
            sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.CANCELLED))
        }
    }

    private fun saveToDownloads() {
        val result = currentResult()
        if (result == null) {
            sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.GENERATION_FAILED))
            return
        }
        if (!exporter.canSaveToDownloads()) {
            sendEffect(ConversionEffect.Message(ConversionEffect.MessageKind.SAVE_UNSUPPORTED))
            return
        }
        launch {
            val ok = exporter.save(result.path)
            sendEffect(
                ConversionEffect.Message(
                    if (ok) ConversionEffect.MessageKind.SAVED
                    else ConversionEffect.MessageKind.SAVE_FAILED,
                ),
            )
        }
    }

    private fun currentResult() = (currentState.phase as? ConversionPhase.Done)?.result

    private fun nextOutputPath(): String {
        if (!outputDir.exists()) outputDir.mkdirs()
        // 新文件会取代上一个结果，旧产物从此不可达 → 先清掉（spec FR-014）
        outputDir.listFiles()?.forEach { if (it.isFile) it.delete() }
        return File(outputDir, "foolproof_${System.currentTimeMillis()}.pdf").absolutePath
    }

    private fun PdfFailure.toMessage(): ConversionEffect.MessageKind = when (this) {
        PdfFailure.NO_CONTENT -> ConversionEffect.MessageKind.NO_CONTENT
        PdfFailure.UNREADABLE -> ConversionEffect.MessageKind.UNREADABLE
        PdfFailure.UNSUPPORTED_ENCODING -> ConversionEffect.MessageKind.UNSUPPORTED_ENCODING
        PdfFailure.OUT_OF_SPACE -> ConversionEffect.MessageKind.OUT_OF_SPACE
        PdfFailure.HEIC_UNSUPPORTED -> ConversionEffect.MessageKind.HEIC_UNSUPPORTED
        PdfFailure.GENERATION_FAILED, PdfFailure.UNKNOWN ->
            ConversionEffect.MessageKind.GENERATION_FAILED
    }
}
