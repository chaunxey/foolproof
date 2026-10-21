package com.foolproof.features.conversion

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.base.util.OutputNaming
import com.foolproof.features.conversion.domain.PdfPageRanges
import com.foolproof.features.conversion.domain.PdfToolEngine
import com.foolproof.features.conversion.domain.PdfToolFile
import com.foolproof.features.conversion.domain.PdfToolMode
import com.foolproof.features.conversion.domain.PdfToolResult
import java.io.File
import kotlinx.coroutines.Job

class PdfToolsViewModel(
    private val engine: PdfToolEngine,
    private val exporter: com.foolproof.features.conversion.domain.PdfExporter,
    private val outputDir: File,
    private val log: LogUtil,
) : BaseViewModel<PdfToolsUiState, PdfToolsEffect>(PdfToolsUiState(), log) {

    private var job: Job? = null
    private var outputPath: String? = null

    init {
        updateState { it.copy(canSaveToDownloads = exporter.canSaveToDownloads()) }
    }

    override fun onAction(action: UiAction) {
        when (action) {
            is PdfToolsAction.ModeChanged -> updateState {
                it.copy(mode = action.mode, output = null, phase = PdfToolPhase.IDLE)
            }

            is PdfToolsAction.PdfsPicked -> addFiles(action.picks)

            is PdfToolsAction.Remove -> updateState { state ->
                state.copy(
                    files = state.files.filterNot { it.uri == action.uri },
                    output = null,
                    phase = PdfToolPhase.IDLE,
                )
            }

            is PdfToolsAction.MoveUp -> updateState { state ->
                val index = state.files.indexOfFirst { it.uri == action.uri }
                if (index <= 0) state else {
                    val list = state.files.toMutableList()
                    list.add(index - 1, list.removeAt(index))
                    state.copy(files = list)
                }
            }

            is PdfToolsAction.RangeChanged -> updateState { it.copy(range = action.range) }
            is PdfToolsAction.RotateChanged -> updateState { it.copy(rotateDegrees = action.degrees) }

            PdfToolsAction.Start -> start()
            PdfToolsAction.Reset -> reset()
            PdfToolsAction.Share -> currentState.output?.let {
                sendEffect(PdfToolsEffect.Share(it.path))
            }
            PdfToolsAction.SaveToDownloads -> save()
        }
    }

    private fun addFiles(picks: List<PdfPick>) {
        if (picks.isEmpty()) return
        launch {
            val existing = currentState.files.map { it.uri }.toSet()
            val added = mutableListOf<PdfToolFile>()
            picks.filterNot { it.uri in existing }.forEach { pick ->
                val pages = engine.pageCount(pick.uri)
                if (pages == null || pages <= 0) {
                    log.w { "skip unreadable pdf: ${pick.uri}" }
                } else {
                    added += PdfToolFile(
                        uri = pick.uri,
                        displayName = pick.displayName,
                        pages = pages,
                    )
                }
            }
            if (added.isNotEmpty()) {
                updateState { state ->
                    val files = state.files + added
                    state.copy(
                        files = files,
                        range = state.range.ifBlank { "1-${files.first().pages}" },
                        output = null,
                        phase = PdfToolPhase.IDLE,
                    )
                }
            } else {
                sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.FAILED))
            }
        }
    }

    private fun start() {
        val state = currentState
        val mode = state.mode
        val outputPath = nextOutputPath(mode)
        this.outputPath = outputPath
        updateState { it.copy(phase = PdfToolPhase.WORKING, output = null) }

        job = launchJob {
            try {
                val result = when (mode) {
                    PdfToolMode.MERGE -> {
                        if (state.files.size < 2) {
                            updateState { it.copy(phase = PdfToolPhase.IDLE) }
                            sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.NEED_TWO))
                            return@launchJob
                        }
                        engine.merge(state.files.map { it.uri }, outputPath)
                    }

                    PdfToolMode.SPLIT -> {
                        val file = state.files.firstOrNull()
                        if (file == null) {
                            updateState { it.copy(phase = PdfToolPhase.IDLE) }
                            sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.NEED_ONE))
                            return@launchJob
                        }
                        val pages = runCatching {
                            PdfPageRanges.parse(state.range, file.pages)
                        }.getOrNull()
                        if (pages.isNullOrEmpty()) {
                            updateState { it.copy(phase = PdfToolPhase.IDLE) }
                            sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.INVALID_RANGE))
                            return@launchJob
                        }
                        engine.split(file.uri, state.range, outputPath)
                    }

                    PdfToolMode.ROTATE -> {
                        val file = state.files.firstOrNull()
                        if (file == null) {
                            updateState { it.copy(phase = PdfToolPhase.IDLE) }
                            sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.NEED_ONE))
                            return@launchJob
                        }
                        engine.rotate(file.uri, state.rotateDegrees, outputPath)
                    }
                }
                updateState { it.copy(phase = PdfToolPhase.DONE, output = result) }
                sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.DONE))
            } catch (throwable: Throwable) {
                log.w { "pdf tool failed: ${throwable::class.simpleName}" }
                outputPath.let { File(it).delete() }
                updateState { it.copy(phase = PdfToolPhase.FAILED) }
                sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.FAILED))
            }
        }
    }

    private fun save() {
        val output = currentState.output ?: return
        launch {
            val ok = exporter.save(output.path)
            sendEffect(
                PdfToolsEffect.Message(
                    if (ok) PdfToolsEffect.MessageKind.SAVED else PdfToolsEffect.MessageKind.SAVE_FAILED,
                ),
            )
        }
    }

    private fun reset() {
        job?.cancel()
        job = null
        outputPath?.let { File(it).delete() }
        outputPath = null
        updateState { PdfToolsUiState() }
    }

    private fun nextOutputPath(mode: PdfToolMode): String {
        if (!outputDir.exists()) outputDir.mkdirs()
        outputDir.listFiles()?.forEach { if (it.isFile) it.delete() }
        val action = when (mode) {
            PdfToolMode.MERGE -> OutputNaming.Action.MERGE
            PdfToolMode.SPLIT -> OutputNaming.Action.SPLIT
            PdfToolMode.ROTATE -> OutputNaming.Action.ROTATE
        }
        return File(
            outputDir,
            OutputNaming.fileName(OutputNaming.MediaType.PDF, action, "pdf"),
        ).absolutePath
    }
}
