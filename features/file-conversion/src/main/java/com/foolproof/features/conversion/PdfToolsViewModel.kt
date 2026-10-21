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
            is PdfToolsAction.ModeChanged -> updateState { state ->
                val needsPages = action.mode == PdfToolMode.PAGES ||
                    action.mode == PdfToolMode.COMPRESS
                val order = if (needsPages) {
                    state.files.firstOrNull()?.let { (1..it.pages).toList() } ?: emptyList()
                } else {
                    state.pageOrder
                }
                state.copy(
                    mode = action.mode,
                    pageOrder = order,
                    output = null,
                    images = emptyList(),
                    phase = PdfToolPhase.IDLE,
                )
            }

            is PdfToolsAction.PdfsPicked -> addFiles(action.picks)

            is PdfToolsAction.Remove -> updateState { state ->
                state.copy(
                    files = state.files.filterNot { it.uri == action.uri },
                    output = null,
                    images = emptyList(),
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
            is PdfToolsAction.QualityChanged -> updateState { it.copy(quality = action.quality) }

            is PdfToolsAction.PageMove -> updateState { state ->
                val from = action.index
                val to = (from + action.delta).coerceIn(0, state.pageOrder.lastIndex)
                if (from !in state.pageOrder.indices || from == to) {
                    state
                } else {
                    val pages = state.pageOrder.toMutableList()
                    pages.add(to, pages.removeAt(from))
                    state.copy(pageOrder = pages, output = null, phase = PdfToolPhase.IDLE)
                }
            }

            is PdfToolsAction.PageRemove -> updateState { state ->
                if (action.index !in state.pageOrder.indices) {
                    state
                } else {
                    val pages = state.pageOrder.toMutableList()
                    pages.removeAt(action.index)
                    state.copy(pageOrder = pages, output = null, phase = PdfToolPhase.IDLE)
                }
            }

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
                    val order = if (state.mode == PdfToolMode.PAGES ||
                        state.mode == PdfToolMode.COMPRESS
                    ) {
                        files.firstOrNull()?.let { (1..it.pages).toList() } ?: emptyList()
                    } else {
                        state.pageOrder
                    }
                    state.copy(
                        files = files,
                        pageOrder = order,
                        range = state.range.ifBlank { "1-${files.first().pages}" },
                        output = null,
                        phase = PdfToolPhase.IDLE,
                    )
                }
            } else {
                sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.LOAD_FAILED))
            }
        }
    }

    private fun start() {
        val state = currentState
        val mode = state.mode
        if (mode == PdfToolMode.IMAGES) {
            startImages(state)
            return
        }
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

                    PdfToolMode.PAGES -> {
                        val file = state.files.firstOrNull()
                        if (file == null) {
                            updateState { it.copy(phase = PdfToolPhase.IDLE) }
                            sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.NEED_ONE))
                            return@launchJob
                        }
                        if (state.pageOrder.isEmpty()) {
                            updateState { it.copy(phase = PdfToolPhase.IDLE) }
                            sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.NEED_PAGES))
                            return@launchJob
                        }
                        engine.rewrite(file.uri, state.pageOrder, outputPath)
                    }

                    PdfToolMode.COMPRESS -> {
                        val file = state.files.firstOrNull()
                        if (file == null) {
                            updateState { it.copy(phase = PdfToolPhase.IDLE) }
                            sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.NEED_ONE))
                            return@launchJob
                        }
                        engine.compress(file.uri, state.quality, outputPath)
                    }

                    PdfToolMode.IMAGES -> error("handled in startImages")
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

    private fun startImages(state: PdfToolsUiState) {
        val file = state.files.firstOrNull()
        if (file == null) {
            sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.NEED_ONE))
            return
        }
        val dir = File(outputDir.parentFile, "pdf_images").apply {
            mkdirs()
            listFiles()?.forEach { if (it.isFile) it.delete() }
        }
        val base = OutputNaming
            .fileName(OutputNaming.MediaType.IMAGE, OutputNaming.Action.CONVERT, "jpg")
            .removeSuffix(".jpg")
        updateState {
            it.copy(phase = PdfToolPhase.WORKING, output = null, images = emptyList())
        }
        job = launchJob {
            try {
                val images = engine.toImages(file.uri, dir.absolutePath, state.quality, base)
                updateState {
                    it.copy(
                        phase = PdfToolPhase.DONE,
                        images = images,
                        output = images.firstOrNull(),
                    )
                }
                sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.DONE))
            } catch (throwable: Throwable) {
                log.w { "pdf to images failed: ${throwable::class.simpleName}" }
                dir.listFiles()?.forEach { if (it.isFile) it.delete() }
                updateState { it.copy(phase = PdfToolPhase.FAILED, images = emptyList()) }
                sendEffect(PdfToolsEffect.Message(PdfToolsEffect.MessageKind.FAILED))
            }
        }
    }

    private fun nextOutputPath(mode: PdfToolMode): String {
        if (!outputDir.exists()) outputDir.mkdirs()
        outputDir.listFiles()?.forEach { if (it.isFile) it.delete() }
        val action = when (mode) {
            PdfToolMode.MERGE -> OutputNaming.Action.MERGE
            PdfToolMode.SPLIT -> OutputNaming.Action.SPLIT
            PdfToolMode.ROTATE -> OutputNaming.Action.ROTATE
            PdfToolMode.PAGES -> OutputNaming.Action.REORDER
            PdfToolMode.COMPRESS -> OutputNaming.Action.COMPRESS
            PdfToolMode.IMAGES -> OutputNaming.Action.CONVERT
        }
        return File(
            outputDir,
            OutputNaming.fileName(OutputNaming.MediaType.PDF, action, "pdf"),
        ).absolutePath
    }
}
