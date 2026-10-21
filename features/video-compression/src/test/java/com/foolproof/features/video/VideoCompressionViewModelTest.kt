package com.foolproof.features.video

import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.domain.BitrateTier
import com.foolproof.features.video.domain.EncoderCapability
import com.foolproof.features.video.domain.EncoderCatalog
import com.foolproof.features.video.domain.ResolutionTier
import com.foolproof.features.video.domain.SourceVideo
import com.foolproof.features.video.domain.VideoCompressRequest
import com.foolproof.features.video.domain.VideoCompressor
import com.foolproof.features.video.domain.VideoExporter
import com.foolproof.features.video.domain.VideoFailure
import com.foolproof.features.video.domain.VideoProgress
import com.foolproof.features.video.domain.VideoReader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class VideoCompressionViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    private fun sourceVideo(
        durationMs: Long = 20_000,
        width: Int = 1920,
        height: Int = 1080,
        hasAudio: Boolean = true,
    ) = SourceVideo(
        id = "content://video/1",
        displayName = "v.mp4",
        sizeBytes = 50_000_000,
        durationMs = durationMs,
        width = width,
        height = height,
        frameRate = 60,
        hasAudio = hasAudio,
    )

    private class FakeReader(
        private val video: SourceVideo?,
        private val fail: Boolean = false,
    ) : VideoReader {
        override suspend fun read(id: String): SourceVideo {
            if (fail || video == null) throw IllegalStateException("unreadable")
            return video
        }
    }

    private class FakeEncoderCatalog(
        private val capability: EncoderCapability?,
    ) : EncoderCatalog {
        override fun findEncoder(mime: String): EncoderCapability? = capability
        override fun availableVideoEncoders(): Set<String> =
            capability?.let { setOf(it.mime) } ?: emptySet()
    }

    private class FakeCompressor(
        private val emitSuccess: Boolean = true,
        private val failure: VideoFailure? = null,
    ) : VideoCompressor {
        var lastRequest: VideoCompressRequest? = null
        var cancelCalled = false
        var cleanedPath: String? = null

        override fun compress(request: VideoCompressRequest): Flow<VideoProgress> = flow {
            lastRequest = request
            emit(VideoProgress.Running(10))
            emit(VideoProgress.Running(60))
            when {
                failure != null -> emit(VideoProgress.Failed(failure))
                emitSuccess -> emit(VideoProgress.Success(request.outputPath, bytes = 12_000_000))
            }
        }

        override suspend fun cancel() {
            cancelCalled = true
        }

        override suspend fun cleanupPartialFile(path: String) {
            cleanedPath = path
        }
    }

    private class FakeExporter(
        private val canSave: Boolean = true,
        private val saveResult: Boolean = true,
    ) : VideoExporter {
        var savedPath: String? = null

        override fun canSaveToGallery(): Boolean = canSave

        override suspend fun saveToGallery(inputPath: String, displayName: String): Boolean {
            savedPath = inputPath
            return saveResult
        }
    }

    private fun hardwareCapability(maxShortSide: Int = 2160) = EncoderCapability(
        mime = "video/avc",
        name = "c2.mtk.avc.encoder",
        isHardware = true,
        maxWidth = maxShortSide,
        maxHeight = maxShortSide,
        maxBitrateKbps = 100_000,
        maxFrameRate = 960.0,
    )

    private fun viewModel(
        reader: VideoReader = FakeReader(sourceVideo()),
        compressor: VideoCompressor = FakeCompressor(),
        catalog: EncoderCatalog = FakeEncoderCatalog(hardwareCapability()),
        exporter: VideoExporter = FakeExporter(),
        outputDir: File = tempFolder.newFolder("videos"),
    ) = VideoCompressionViewModel(
        reader = reader,
        compressor = compressor,
        encoderCatalog = catalog,
        exporter = exporter,
        outputDir = outputDir,
        log = LogUtil(enabled = false),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `picking a video loads metadata and prepares full trim range`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertNotNull(state.video)
        assertEquals(VideoPhase.Ready, state.phase)
        assertEquals(0L, state.trim.startMs)
        assertEquals(20_000L, state.trim.endMs)
        assertTrue(state.canCompress)
    }

    @Test
    fun `unreadable video fails with message`() = runTest(dispatcher) {
        val vm = viewModel(reader = FakeReader(null, fail = true))
        val effects = mutableListOf<VideoEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(VideoAction.VideoPicked("content://bad"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.phase is VideoPhase.Failed)
        assertTrue(
            effects.any {
                it is VideoEffect.Message && it.kind == VideoEffect.MessageKind.SOURCE_UNREADABLE
            },
        )
        job.cancel()
    }

    @Test
    fun `missing encoder blocks compression`() = runTest(dispatcher) {
        val vm = viewModel(catalog = FakeEncoderCatalog(null))

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.phase is VideoPhase.Failed)
        assertFalse(vm.uiState.value.canCompress)
    }

    @Test
    fun `software-only encoder warns but still allows compression`() = runTest(dispatcher) {
        val software = hardwareCapability().copy(isHardware = false)
        val vm = viewModel(catalog = FakeEncoderCatalog(software))
        val effects = mutableListOf<VideoEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()

        assertEquals(VideoPhase.Ready, vm.uiState.value.phase)
        assertNotNull("必须显示软编提示，不能静默", vm.uiState.value.encoderNote)
        assertTrue(
            effects.any {
                it is VideoEffect.Message && it.kind == VideoEffect.MessageKind.SOFTWARE_ENCODER_ONLY
            },
        )
        job.cancel()
    }

    @Test
    fun `trim change updates estimate duration`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()

        val fullEstimate = vm.uiState.value.estimatedBytes
        vm.dispatch(VideoAction.TrimChanged(3_000, 8_000))
        advanceUntilIdle()

        val trimmedEstimate = vm.uiState.value.estimatedBytes
        assertEquals(5_000L, vm.uiState.value.trimDurationMs)
        assertTrue("裁剪后预估必须变小", trimmedEstimate < fullEstimate)
    }

    @Test
    fun `compression runs to success and records output`() = runTest(dispatcher) {
        val compressor = FakeCompressor()
        val vm = viewModel(compressor = compressor)

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.Start)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(VideoPhase.Done, state.phase)
        assertNotNull(state.output)
        assertEquals(12_000_000L, state.output?.bytes)
        assertEquals(50_000_000L - 12_000_000L, state.savedBytes)

        val request = compressor.lastRequest
        assertNotNull(request)
        assertEquals(720, request!!.targetShortSide)
        assertEquals(2_500, request.videoBitrateKbps)
        assertEquals(30, request.frameRate)
        assertTrue(request.outputPath.endsWith(".mp4"))
    }

    @Test
    fun `compression failure surfaces classified message`() = runTest(dispatcher) {
        val vm = viewModel(compressor = FakeCompressor(failure = VideoFailure.NO_ENCODER))
        val effects = mutableListOf<VideoEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.Start)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.phase is VideoPhase.Failed)
        assertTrue(
            effects.any {
                it is VideoEffect.Message && it.kind == VideoEffect.MessageKind.NO_ENCODER
            },
        )
        job.cancel()
    }

    @Test
    fun `cancel stops compression and cleans partial file`() = runTest(dispatcher) {
        val compressor = FakeCompressor()
        val vm = viewModel(compressor = compressor)

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.Start)
        advanceUntilIdle()

        vm.dispatch(VideoAction.Cancel)
        advanceUntilIdle()

        assertTrue(compressor.cancelCalled)
        assertNotNull("取消后必须清理半成品", compressor.cleanedPath)
        assertEquals(VideoPhase.Ready, vm.uiState.value.phase)
    }

    @Test
    fun `resolution and bitrate changes update request`() = runTest(dispatcher) {
        val compressor = FakeCompressor()
        val vm = viewModel(compressor = compressor)

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.ResolutionChanged(ResolutionTier.P480))
        vm.dispatch(VideoAction.BitrateChanged(BitrateTier.LOW))
        advanceUntilIdle()
        vm.dispatch(VideoAction.Start)
        advanceUntilIdle()

        val request = compressor.lastRequest!!
        assertEquals(480, request.targetShortSide)
        assertEquals(700, request.videoBitrateKbps)
    }

    @Test
    fun `resolution is capped by encoder capability`() = runTest(dispatcher) {
        val vm = viewModel(catalog = FakeEncoderCatalog(hardwareCapability(maxShortSide = 720)))

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.ResolutionChanged(ResolutionTier.P1080))
        advanceUntilIdle()

        assertEquals(ResolutionTier.P720, vm.uiState.value.preset.resolution)
    }

    @Test
    fun `resolution is capped by source size to avoid upscaling`() = runTest(dispatcher) {
        // 源是 640x480（短边 480），选 1080p 应降到 480p，界面不能显示 1080p
        val vm = viewModel(reader = FakeReader(sourceVideo(width = 640, height = 480)))
        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()

        assertEquals(ResolutionTier.P480, vm.uiState.value.preset.resolution)

        vm.dispatch(VideoAction.ResolutionChanged(ResolutionTier.P1080))
        advanceUntilIdle()
        assertEquals(ResolutionTier.P480, vm.uiState.value.preset.resolution)
    }

    @Test
    fun `source without audio drops audio in estimate`() = runTest(dispatcher) {
        val vm = viewModel(reader = FakeReader(sourceVideo(hasAudio = false)))
        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()

        // 720p/中 = 2500kbps，无音频 → 20s = 6.25MB
        assertEquals(6_250_000L, vm.uiState.value.estimatedBytes)
    }

    @Test
    fun `save to gallery reports success`() = runTest(dispatcher) {
        val exporter = FakeExporter(canSave = true, saveResult = true)
        val vm = viewModel(exporter = exporter)
        val effects = mutableListOf<VideoEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.Start)
        advanceUntilIdle()
        vm.dispatch(VideoAction.SaveToGallery)
        advanceUntilIdle()

        assertNotNull("必须真的调用了导出", exporter.savedPath)
        assertTrue(
            effects.any { it is VideoEffect.Message && it.kind == VideoEffect.MessageKind.SAVED },
        )
        job.cancel()
    }

    @Test
    fun `save is refused below api 29`() = runTest(dispatcher) {
        val vm = viewModel(exporter = FakeExporter(canSave = false))
        val effects = mutableListOf<VideoEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.Start)
        advanceUntilIdle()
        vm.dispatch(VideoAction.SaveToGallery)
        advanceUntilIdle()

        assertTrue(
            effects.any {
                it is VideoEffect.Message && it.kind == VideoEffect.MessageKind.SAVE_UNSUPPORTED
            },
        )
        job.cancel()
    }

    @Test
    fun `orphan outputs from a previous process are cleaned on start`() = runTest(dispatcher) {
        val dir = tempFolder.newFolder("videos-orphan")
        val stale = File(dir, "stale.mp4").apply {
            writeText("partial")
            setLastModified(System.currentTimeMillis() - 60_000)
        }

        // VM 构造后、清理协程执行前，模拟"本进程产生的文件"
        val vm = viewModel(outputDir = dir)
        val current = File(dir, "current.mp4").apply { writeText("fresh") }

        advanceUntilIdle()

        assertFalse("上一进程的残留应被清理（FR-014）", stale.exists())
        assertTrue("本进程内的文件不应被误删", current.exists())
        assertNotNull(vm)
    }

    @Test
    fun `starting a new compression removes the previous output`() = runTest(dispatcher) {
        val dir = tempFolder.newFolder("videos-replace")
        val previous = File(dir, "compressed_old.mp4").apply { writeText("previous") }

        val vm = viewModel(outputDir = dir)
        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.Start)
        advanceUntilIdle()

        assertFalse("上一次的产物应被清掉（FR-014）", previous.exists())
    }

    @Test
    fun `share emits the output path`() = runTest(dispatcher) {
        val vm = viewModel()
        val effects = mutableListOf<VideoEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.Start)
        advanceUntilIdle()
        vm.dispatch(VideoAction.Share)
        advanceUntilIdle()

        assertTrue(effects.any { it is VideoEffect.Share })
        job.cancel()
    }

    @Test
    fun `save button support follows exporter capability`() = runTest(dispatcher) {
        // API < 29：FakeExporter 报不支持 → 状态里 saveSupported 必须为 false（FR-013）
        val vm = viewModel(exporter = FakeExporter(canSave = false))
        assertFalse(vm.uiState.value.saveSupported)
    }

    @Test
    fun `failure cleans up the partial output`() = runTest(dispatcher) {
        val compressor = FakeCompressor(failure = VideoFailure.TRANSCODE_FAILED)
        val vm = viewModel(compressor = compressor)

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.Start)
        advanceUntilIdle()

        assertNotNull("失败后必须清理半成品（FR-014）", compressor.cleanedPath)
    }

    @Test
    fun `long video emits a trim suggestion`() = runTest(dispatcher) {
        // 31 分钟的视频 → 提示建议先裁剪（规格「边界情况」）
        val vm = viewModel(reader = FakeReader(sourceVideo(durationMs = 31 * 60 * 1000L)))
        val effects = mutableListOf<VideoEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()

        assertTrue(
            effects.any {
                it is VideoEffect.Message && it.kind == VideoEffect.MessageKind.LONG_VIDEO
            },
        )
        job.cancel()
    }

    @Test
    fun `short video does not emit the trim suggestion`() = runTest(dispatcher) {
        val vm = viewModel(reader = FakeReader(sourceVideo(durationMs = 20_000)))
        val effects = mutableListOf<VideoEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()

        assertTrue(
            effects.none {
                it is VideoEffect.Message && it.kind == VideoEffect.MessageKind.LONG_VIDEO
            },
        )
        job.cancel()
    }
}
