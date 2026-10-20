package com.foolproof.features.compression

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.features.compression.domain.CompressionFailure
import com.foolproof.features.compression.domain.CompressionOption
import com.foolproof.features.compression.domain.CompressionOutcome
import com.foolproof.features.compression.domain.FailureReason
import com.foolproof.features.compression.domain.ImageCompressor
import com.foolproof.features.compression.domain.ImageMetadataReader
import com.foolproof.features.compression.domain.OutcomeStatus
import com.foolproof.features.compression.domain.ResultExporter
import com.foolproof.features.compression.domain.SourceImage
import com.foolproof.features.compression.platform.PlatformCapabilities
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield

/**
 * 图片压缩的 ViewModel（宪法 §4：状态走 StateFlow、副作用走 Channel、异常走基类 onError）。
 */
class CompressionViewModel(
    private val compressor: ImageCompressor,
    private val exporter: ResultExporter,
    private val metadataReader: ImageMetadataReader,
    private val caps: PlatformCapabilities,
    logger: LogUtil,
) : BaseViewModel<CompressionUiState, CompressionEffect>(
    CompressionUiState(saveSupported = exporter.canSaveToGallery()),
    logger,
) {

    private var batchJob: Job? = null
    private var estimateJob: Job? = null

    override fun onAction(action: UiAction) {
        when (action) {
            CompressionAction.SelectImages -> sendEffect(CompressionEffect.OpenPicker)

            is CompressionAction.ImagesPicked -> loadImages(action.ids)

            CompressionAction.Start -> startBatch(ids = null)

            CompressionAction.Cancel -> cancelBatch()

            is CompressionAction.Retry -> startBatch(ids = listOf(action.id))

            is CompressionAction.ChangeFormat -> changeOption { it.withFormat(action.format) }

            is CompressionAction.ChangeQuality -> changeOption { it.withQuality(action.quality) }

            is CompressionAction.Share -> share(action.id)

            is CompressionAction.SaveToGallery -> saveToGallery(action.id)

            CompressionAction.Clear -> clear()
        }
    }

    // region 选图

    private fun loadImages(ids: List<String>) {
        if (ids.isEmpty()) {
            sendEffect(CompressionEffect.Message(CompressionEffect.MessageKind.NOTHING_SELECTED))
            return
        }
        launch {
            val images = metadataReader.read(ids)
            if (images.isEmpty()) {
                sendEffect(CompressionEffect.Message(CompressionEffect.MessageKind.NOTHING_SELECTED))
                return@launch
            }
            updateState { state ->
                state.copy(
                    images = images,
                    outcomes = images.associate { image ->
                        image.id to CompressionOutcome(id = image.id, originalBytes = image.sizeBytes)
                    },
                )
            }
        }
    }

    // endregion

    // region 批量压缩

    private fun startBatch(ids: List<String>?) {
        if (currentState.isRunning) return

        val targets = currentState.images.filter { ids == null || it.id in ids }
        if (targets.isEmpty()) {
            sendEffect(CompressionEffect.Message(CompressionEffect.MessageKind.NOTHING_SELECTED))
            return
        }

        updateState { it.copy(isRunning = true) }
        batchJob = launchJob {
            val option = currentState.option
            for (image in targets) {
                updateOutcome(image.id) { it.copy(status = OutcomeStatus.RUNNING, failure = null) }
                yield()

                if (image.isHeic && !caps.isHeicDecodable) {
                    updateOutcome(image.id) {
                        it.copy(
                            status = OutcomeStatus.UNSUPPORTED,
                            failure = FailureReason.UNSUPPORTED_FORMAT,
                        )
                    }
                    continue
                }

                try {
                    val file = compressor.compress(image, option)
                    updateOutcome(image.id) {
                        it.copy(
                            status = if (file.bytes < image.sizeBytes) {
                                OutcomeStatus.SUCCESS
                            } else {
                                OutcomeStatus.NO_GAIN
                            },
                            compressedBytes = file.bytes,
                            outputPath = file.path,
                            failure = null,
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: CompressionFailure) {
                    updateOutcome(image.id) {
                        it.copy(status = OutcomeStatus.FAILED, failure = failure.reason)
                    }
                } catch (throwable: Throwable) {
                    updateOutcome(image.id) {
                        it.copy(status = OutcomeStatus.FAILED, failure = FailureReason.UNKNOWN)
                    }
                }
            }
            updateState { it.copy(isRunning = false) }
        }
    }

    private fun cancelBatch() {
        batchJob?.cancel()
        batchJob = null
        updateState { state ->
            state.copy(
                isRunning = false,
                outcomes = state.outcomes.mapValues { (_, outcome) ->
                    if (outcome.status == OutcomeStatus.RUNNING) {
                        outcome.copy(status = OutcomeStatus.PENDING)
                    } else {
                        outcome
                    }
                },
            )
        }
    }

    // endregion

    // region 参数与预估

    private fun changeOption(transform: (CompressionOption) -> CompressionOption) {
        val option = transform(currentState.option)
        updateState { it.copy(option = option) }
        scheduleEstimate(option)
    }

    private fun scheduleEstimate(option: CompressionOption) {
        estimateJob?.cancel()

        val first = currentState.images.firstOrNull()
        if (first == null) {
            updateState { it.copy(estimateBytes = null, estimateLoading = false) }
            return
        }

        updateState { it.copy(estimateLoading = true) }
        estimateJob = launchJob {
            delay(ESTIMATE_DEBOUNCE_MS)
            val bytes = try {
                compressor.compress(first, option).bytes
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                null
            }
            updateState { it.copy(estimateBytes = bytes, estimateLoading = false) }
        }
    }

    // endregion

    // region 导出

    private fun share(id: String) {
        val path = currentState.outcomeOf(id).outputPath
        if (path == null) {
            sendFailed(CompressionEffect.MessageKind.SHARE_FAILED)
            return
        }
        launch { if (!exporter.share(path)) sendFailed(CompressionEffect.MessageKind.SHARE_FAILED) }
    }

    private fun saveToGallery(id: String) {
        if (!exporter.canSaveToGallery()) {
            sendFailed(CompressionEffect.MessageKind.SAVE_UNSUPPORTED)
            return
        }
        val path = currentState.outcomeOf(id).outputPath
        if (path == null) {
            sendFailed(CompressionEffect.MessageKind.SAVE_FAILED)
            return
        }
        launch { if (!exporter.saveToGallery(path)) sendFailed(CompressionEffect.MessageKind.SAVE_FAILED) }
    }

    private fun sendFailed(kind: CompressionEffect.MessageKind) {
        sendEffect(CompressionEffect.Message(kind))
    }

    // endregion

    private fun clear() {
        batchJob?.cancel()
        estimateJob?.cancel()
        batchJob = null
        estimateJob = null
        updateState {
            CompressionUiState(option = it.option)
        }
    }

    private fun updateOutcome(id: String, transform: (CompressionOutcome) -> CompressionOutcome) {
        updateState { state ->
            val current = state.outcomeOf(id)
            state.copy(outcomes = state.outcomes + (id to transform(current)))
        }
    }

    private companion object {
        const val ESTIMATE_DEBOUNCE_MS = 300L
    }
}
