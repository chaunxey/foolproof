package com.foolproof.features.compression

import com.foolproof.base.log.LogUtil
import com.foolproof.features.compression.domain.CompressedFile
import com.foolproof.features.compression.domain.CompressionFailure
import com.foolproof.features.compression.domain.CompressionOption
import com.foolproof.features.compression.domain.FailureReason
import com.foolproof.features.compression.domain.ImageCompressor
import com.foolproof.features.compression.domain.ImageMetadataReader
import com.foolproof.features.compression.domain.OutcomeStatus
import com.foolproof.features.compression.domain.ResultExporter
import com.foolproof.features.compression.domain.SourceImage
import com.foolproof.features.compression.platform.PlatformCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompressionViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private class FakeCompressor(
        private val bytesFor: (SourceImage) -> Long = { COMPRESSED_BYTES },
        private val failures: MutableMap<String, FailureReason> = mutableMapOf(),
        private val delayMs: Long = 0L,
    ) : ImageCompressor {

        val calls = mutableListOf<String>()

        fun failOn(id: String) {
            failures[id] = FailureReason.DECODE
        }

        fun recover(id: String) {
            failures.remove(id)
        }

        override suspend fun compress(image: SourceImage, option: CompressionOption): CompressedFile {
            calls += image.id
            if (delayMs > 0) delay(delayMs)
            failures[image.id]?.let { throw CompressionFailure(it) }
            return CompressedFile(path = "/tmp/${image.id}", bytes = bytesFor(image))
        }

        companion object {
            const val COMPRESSED_BYTES = 1_000L
        }
    }

    private class FakeReader(private val images: List<SourceImage>) : ImageMetadataReader {
        override suspend fun read(ids: List<String>): List<SourceImage> = images.filter { it.id in ids }
    }

    private class FakeExporter(private val canSave: Boolean = true) : ResultExporter {
        val shared = mutableListOf<String>()
        val saved = mutableListOf<String>()

        override fun canSaveToGallery(): Boolean = canSave

        override suspend fun share(id: String): Boolean {
            shared += id
            return true
        }

        override suspend fun saveToGallery(id: String): Boolean {
            saved += id
            return true
        }
    }

    private fun image(id: String, sizeBytes: Long = 4_000L, mime: String? = "image/jpeg") = SourceImage(
        id = id,
        displayName = "$id.jpg",
        sizeBytes = sizeBytes,
        mimeType = mime,
        width = 4000,
        height = 3000,
    )

    private fun viewModel(
        images: List<SourceImage> = listOf(image("a"), image("b"), image("c")),
        compressor: FakeCompressor = FakeCompressor(),
        exporter: FakeExporter = FakeExporter(),
        sdkInt: Int = 33,
    ): Pair<CompressionViewModel, FakeCompressor> {
        val reader = FakeReader(images)
        val vm = CompressionViewModel(
            compressor = compressor,
            exporter = exporter,
            metadataReader = reader,
            caps = PlatformCapabilities(sdkInt),
            logger = LogUtil(enabled = false),
        )
        return vm to compressor
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `picked images are loaded with outcomes initialised`() = runTest(dispatcher) {
        val (vm, _) = viewModel()

        vm.dispatch(CompressionAction.ImagesPicked(listOf("a", "b")))
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.images.size)
        assertEquals(2, vm.uiState.value.outcomes.size)
        assertTrue(vm.uiState.value.canStart)
    }

    @Test
    fun `start compresses every image and records sizes`() = runTest(dispatcher) {
        val (vm, compressor) = viewModel()

        vm.dispatch(CompressionAction.ImagesPicked(listOf("a", "b", "c")))
        advanceUntilIdle()
        vm.dispatch(CompressionAction.Start)
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), compressor.calls)
        assertFalse(vm.uiState.value.isRunning)
        assertEquals(3, vm.uiState.value.completedCount)
        assertTrue(
            vm.uiState.value.outcomes.values.all { it.status == OutcomeStatus.SUCCESS },
        )
        assertEquals(9_000L, vm.uiState.value.totalSavedBytes)
    }

    @Test
    fun `start without images emits a message`() = runTest(dispatcher) {
        val (vm, compressor) = viewModel(images = emptyList())
        val effects = mutableListOf<CompressionEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(CompressionAction.Start)
        advanceUntilIdle()

        assertTrue(compressor.calls.isEmpty())
        assertTrue(
            effects.any {
                it is CompressionEffect.Message &&
                    it.kind == CompressionEffect.MessageKind.NOTHING_SELECTED
            },
        )
        job.cancel()
    }

    @Test
    fun `a failing image does not break the rest of the batch`() = runTest(dispatcher) {
        val compressor = FakeCompressor()
        compressor.failOn("b")
        val (vm, _) = viewModel(compressor = compressor)

        vm.dispatch(CompressionAction.ImagesPicked(listOf("a", "b", "c")))
        advanceUntilIdle()
        vm.dispatch(CompressionAction.Start)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(OutcomeStatus.SUCCESS, state.outcomeOf("a").status)
        assertEquals(OutcomeStatus.FAILED, state.outcomeOf("b").status)
        assertEquals(FailureReason.DECODE, state.outcomeOf("b").failure)
        assertEquals(OutcomeStatus.SUCCESS, state.outcomeOf("c").status)
    }

    @Test
    fun `heic is unsupported on api 27 and skipped without calling compressor`() = runTest(dispatcher) {
        val heic = image("h", mime = "image/heic")
        val compressor = FakeCompressor()
        val (vm, _) = viewModel(images = listOf(heic), compressor = compressor, sdkInt = 27)

        vm.dispatch(CompressionAction.ImagesPicked(listOf("h")))
        advanceUntilIdle()
        vm.dispatch(CompressionAction.Start)
        advanceUntilIdle()

        assertEquals(OutcomeStatus.UNSUPPORTED, vm.uiState.value.outcomeOf("h").status)
        assertTrue(compressor.calls.isEmpty())
    }

    @Test
    fun `heic is processed on api 28`() = runTest(dispatcher) {
        val heic = image("h", mime = "image/heic")
        val compressor = FakeCompressor()
        val (vm, _) = viewModel(images = listOf(heic), compressor = compressor, sdkInt = 28)

        vm.dispatch(CompressionAction.ImagesPicked(listOf("h")))
        advanceUntilIdle()
        vm.dispatch(CompressionAction.Start)
        advanceUntilIdle()

        assertEquals(listOf("h"), compressor.calls)
        assertEquals(OutcomeStatus.SUCCESS, vm.uiState.value.outcomeOf("h").status)
    }

    @Test
    fun `estimate is debounced to a single call`() = runTest(dispatcher) {
        val (vm, compressor) = viewModel()

        vm.dispatch(CompressionAction.ImagesPicked(listOf("a", "b")))
        advanceUntilIdle()
        assertTrue(compressor.calls.isEmpty())

        vm.dispatch(CompressionAction.ChangeQuality(70))
        advanceTimeBy(100)
        vm.dispatch(CompressionAction.ChangeQuality(60))
        advanceTimeBy(100)
        vm.dispatch(CompressionAction.ChangeQuality(50))

        advanceTimeBy(299)
        assertTrue("防抖窗口内不应触发", compressor.calls.isEmpty())

        advanceTimeBy(2)
        assertEquals(listOf("a"), compressor.calls)
        assertEquals(1_000L, vm.uiState.value.estimateBytes)
        assertFalse(vm.uiState.value.estimateLoading)
    }

    @Test
    fun `estimate is skipped when nothing is selected`() = runTest(dispatcher) {
        val (vm, compressor) = viewModel(images = emptyList())

        vm.dispatch(CompressionAction.ChangeQuality(50))
        advanceUntilIdle()

        assertTrue(compressor.calls.isEmpty())
        assertNull(vm.uiState.value.estimateBytes)
    }

    @Test
    fun `cancel stops the batch and resets the running item`() = runTest(dispatcher) {
        val compressor = FakeCompressor(delayMs = 1_000L)
        val (vm, _) = viewModel(compressor = compressor)

        vm.dispatch(CompressionAction.ImagesPicked(listOf("a", "b", "c")))
        advanceUntilIdle()
        vm.dispatch(CompressionAction.Start)
        advanceTimeBy(100)
        vm.dispatch(CompressionAction.Cancel)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isRunning)
        assertEquals(listOf("a"), compressor.calls)
        assertEquals(OutcomeStatus.PENDING, state.outcomeOf("a").status)
        assertEquals(OutcomeStatus.PENDING, state.outcomeOf("b").status)
    }

    @Test
    fun `failed image can be retried individually`() = runTest(dispatcher) {
        val compressor = FakeCompressor()
        compressor.failOn("b")
        val (vm, _) = viewModel(compressor = compressor)

        vm.dispatch(CompressionAction.ImagesPicked(listOf("a", "b")))
        advanceUntilIdle()
        vm.dispatch(CompressionAction.Start)
        advanceUntilIdle()
        assertEquals(OutcomeStatus.FAILED, vm.uiState.value.outcomeOf("b").status)

        compressor.recover("b")
        vm.dispatch(CompressionAction.Retry("b"))
        advanceUntilIdle()

        assertEquals(OutcomeStatus.SUCCESS, vm.uiState.value.outcomeOf("b").status)
        assertEquals(OutcomeStatus.SUCCESS, vm.uiState.value.outcomeOf("a").status)
    }

    @Test
    fun `share and save delegate to exporter`() = runTest(dispatcher) {
        val exporter = FakeExporter(canSave = true)
        val images = listOf(image("a"))
        val compressor = FakeCompressor()
        val vm = CompressionViewModel(
            compressor = compressor,
            exporter = exporter,
            metadataReader = FakeReader(images),
            caps = PlatformCapabilities(33),
            logger = LogUtil(enabled = false),
        )

        vm.dispatch(CompressionAction.ImagesPicked(listOf("a")))
        advanceUntilIdle()
        vm.dispatch(CompressionAction.Start)
        advanceUntilIdle()

        // 导出器拿到的是压缩结果的文件路径，不是图片 id
        val outputPath = vm.uiState.value.outcomeOf("a").outputPath
        assertEquals("/tmp/a", outputPath)

        vm.dispatch(CompressionAction.Share("a"))
        vm.dispatch(CompressionAction.SaveToGallery("a"))
        advanceUntilIdle()

        assertEquals(listOf(outputPath), exporter.shared)
        assertEquals(listOf(outputPath), exporter.saved)
    }

    @Test
    fun `successful gallery save emits a confirmation`() = runTest(dispatcher) {
        val exporter = FakeExporter(canSave = true)
        val images = listOf(image("a"))
        val vm = CompressionViewModel(
            compressor = FakeCompressor(),
            exporter = exporter,
            metadataReader = FakeReader(images),
            caps = PlatformCapabilities(33),
            logger = LogUtil(enabled = false),
        )
        val effects = mutableListOf<CompressionEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(CompressionAction.ImagesPicked(listOf("a")))
        advanceUntilIdle()
        vm.dispatch(CompressionAction.Start)
        advanceUntilIdle()
        vm.dispatch(CompressionAction.SaveToGallery("a"))
        advanceUntilIdle()

        assertTrue(
            "保存成功后必须给出反馈，否则用户会重复点击",
            effects.any {
                it is CompressionEffect.Message && it.kind == CompressionEffect.MessageKind.SAVED
            },
        )
        job.cancel()
    }

    @Test
    fun `save to gallery is refused below api 29`() = runTest(dispatcher) {
        val exporter = FakeExporter(canSave = false)
        val vm = CompressionViewModel(
            compressor = FakeCompressor(),
            exporter = exporter,
            metadataReader = FakeReader(emptyList()),
            caps = PlatformCapabilities(28),
            logger = LogUtil(enabled = false),
        )
        val effects = mutableListOf<CompressionEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(CompressionAction.SaveToGallery("a"))
        advanceUntilIdle()

        assertTrue(exporter.saved.isEmpty())
        assertTrue(
            effects.any {
                it is CompressionEffect.Message &&
                    it.kind == CompressionEffect.MessageKind.SAVE_UNSUPPORTED
            },
        )
        job.cancel()
    }
}
