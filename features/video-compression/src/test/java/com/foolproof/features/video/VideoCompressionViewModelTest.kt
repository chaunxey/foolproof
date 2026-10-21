package com.foolproof.features.video

import com.foolproof.base.log.LogUtil
import com.foolproof.features.video.domain.BitrateTier
import com.foolproof.features.video.domain.EncoderCapability
import com.foolproof.features.video.domain.EncoderCatalog
import com.foolproof.features.video.domain.ResolutionTier
import com.foolproof.features.video.domain.SourceVideo
import com.foolproof.features.video.domain.VideoCompressRequest
import com.foolproof.features.video.domain.VideoCompressor
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

    private fun hardwareCapability(maxShortSide: Int = 2160) = EncoderCapability(
        mime = "video/avc",
        name = "c2.mtk.avc.encoder",
        isHardware = true,
        maxWidth = 7680,
        maxHeight = 7680,
        maxBitrateKbps = 100_000,
        maxFrameRate = 960.0,
    ).copy(maxWidth = maxShortSide, maxHeight = maxShortSide)

    private fun viewModel(
        reader: VideoReader = FakeReader(sourceVideo()),
        compressor: VideoCompressor = FakeCompressor(),
        catalog: EncoderCatalog = FakeEncoderCatalog(hardwareCapability()),
        outputDir: File = tempFolder.newFolder("videos"),
    ) = VideoCompressionViewModel(
        reader = reader,
        compressor = compressor,
        encoderCatalog = catalog,
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
        assertEquals(720, request!!.targetShortSide)          // 默认 720p
        assertEquals(2_500, request.videoBitrateKbps)         // 中档
        assertEquals(30, request.frameRate)                   // 源 60fps → 压到 30
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
        // 编码器最大短边只有 720 → 选 1080p 应自动降档
        val vm = viewModel(catalog = FakeEncoderCatalog(hardwareCapability(maxShortSide = 720)))

        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()
        vm.dispatch(VideoAction.ResolutionChanged(ResolutionTier.P1080))
        advanceUntilIdle()

        assertEquals(ResolutionTier.P720, vm.uiState.value.preset.resolution)
    }

    @Test
    fun `source without audio drops audio in estimate`() = runTest(dispatcher) {
        val vm = viewModel(reader = FakeReader(sourceVideo(hasAudio = false)))
        vm.dispatch(VideoAction.VideoPicked("content://video/1"))
        advanceUntilIdle()

        // 720p/中 = 2500kbps，无音频 → 20s = 6.25MB
        assertEquals(6_250_000L, vm.uiState.value.estimateOrZero())
    }

    private fun VideoUiState.estimateOrZero(): Long = estimatedBytes
}
