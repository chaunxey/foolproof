package com.foolproof.features.conversion

import com.foolproof.base.log.LogUtil
import com.foolproof.features.conversion.domain.PdfExporter
import com.foolproof.features.conversion.domain.PdfConvertRequest
import com.foolproof.features.conversion.domain.PdfConverter
import com.foolproof.features.conversion.domain.PdfFailure
import com.foolproof.features.conversion.domain.PdfImageLoader
import com.foolproof.features.conversion.domain.PdfPageSpec
import com.foolproof.features.conversion.domain.PdfProgress
import com.foolproof.features.conversion.domain.PdfResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileConversionViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeImageLoader : PdfImageLoader {
        override suspend fun load(uri: String) = android.graphics.Bitmap.createBitmap(2, 2, android.graphics.Bitmap.Config.ARGB_8888)
        override suspend fun readSize(uri: String) = 100 to 200
    }

    private class FakeConverter(
        private val failWith: PdfFailure? = null,
        private val pages: Int = 2,
    ) : PdfConverter {
        var lastRequest: PdfConvertRequest? = null
        var cleanedPath: String? = null

        override fun convert(request: PdfConvertRequest): Flow<PdfProgress> = flow {
            lastRequest = request
            emit(PdfProgress.Running(1, pages))
            if (failWith != null) {
                emit(PdfProgress.Failed(failWith))
            } else {
                emit(PdfProgress.Success(PdfResult(request.outputPath, 1024L, pages)))
            }
        }

        override suspend fun cancel() = Unit
        override suspend fun cleanupPartialFile(path: String) {
            cleanedPath = path
        }
    }

    private fun viewModel(
        converter: PdfConverter = FakeConverter(),
        canSave: Boolean = true,
    ): FileConversionViewModel {
        return FileConversionViewModel(
            converter = converter,
            imageLoader = FakeImageLoader(),
            exporter = FakeExporter(canSave),
            outputDir = tempFolder.newFolder("pdf"),
            log = LogUtil(enabled = false),
        )
    }

    @Test
    fun `images picked store width and height`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.dispatch(ConversionAction.ImagesPicked(listOf("content://a", "content://b")))
        testScheduler.advanceUntilIdle()

        assertEquals(2, vm.uiState.value.images.size)
        assertEquals(100, vm.uiState.value.images.first().width)
    }

    @Test
    fun `more than fifty images are capped`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.dispatch(ConversionAction.ImagesPicked((1..60).map { "content://img$it" }))
        testScheduler.advanceUntilIdle()

        assertEquals(PdfPageSpec.MAX_IMAGES, vm.uiState.value.images.size)
    }

    @Test
    fun `remove and move reorder the list`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.dispatch(ConversionAction.ImagesPicked(listOf("a", "b", "c")))
        testScheduler.advanceUntilIdle()

        vm.dispatch(ConversionAction.MoveImage(2, 0))
        assertEquals("c", vm.uiState.value.images.first().uri)

        vm.dispatch(ConversionAction.RemoveImage(0))
        assertEquals(2, vm.uiState.value.images.size)
    }

    @Test
    fun `text larger than two megabytes is rejected`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.dispatch(ConversionAction.TextPicked("content://t", "big.md", PdfPageSpec.MAX_TEXT_BYTES + 1))
        testScheduler.advanceUntilIdle()

        assertEquals(null, vm.uiState.value.text)
    }

    @Test
    fun `start produces done with page count`() = runTest(dispatcher) {
        val vm = viewModel(converter = FakeConverter(pages = 3))
        vm.dispatch(ConversionAction.ImagesPicked(listOf("a")))
        testScheduler.advanceUntilIdle()
        vm.dispatch(ConversionAction.Start)
        testScheduler.advanceUntilIdle()

        val phase = vm.uiState.value.phase
        assertTrue(phase is ConversionPhase.Done)
        assertEquals(3, (phase as ConversionPhase.Done).result.pageCount)
    }

    @Test
    fun `failure cleans the partial file`() = runTest(dispatcher) {
        val converter = FakeConverter(failWith = PdfFailure.HEIC_UNSUPPORTED)
        val vm = viewModel(converter = converter)
        vm.dispatch(ConversionAction.ImagesPicked(listOf("a")))
        testScheduler.advanceUntilIdle()
        vm.dispatch(ConversionAction.Start)
        testScheduler.advanceUntilIdle()

        assertTrue(vm.uiState.value.phase is ConversionPhase.Failed)
        assertNotNull("失败必须清理半成品（FR-014）", converter.cleanedPath)
    }

    @Test
    fun `switching mode resets the phase`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.dispatch(ConversionAction.ImagesPicked(listOf("a")))
        testScheduler.advanceUntilIdle()

        vm.dispatch(ConversionAction.ModeChanged(ConversionMode.TEXT))
        assertTrue(vm.uiState.value.phase is ConversionPhase.Idle)
        assertEquals(ConversionMode.TEXT, vm.uiState.value.mode)
    }

    @Test
    fun `cannot start without sources`() {
        val vm = viewModel()
        assertFalse(vm.uiState.value.canConvert)
    }

    @Test
    fun `new output path removes the previous result`() = runTest(dispatcher) {
        val dir = tempFolder.newFolder("pdf-clean")
        val old = File(dir, "old.pdf").apply { writeText("x") }
        val vm = FileConversionViewModel(
            converter = FakeConverter(),
            imageLoader = FakeImageLoader(),
            exporter = FakeExporter(canSave = false),
            outputDir = dir,
            log = LogUtil(enabled = false),
        )

        vm.dispatch(ConversionAction.ImagesPicked(listOf("a")))
        testScheduler.advanceUntilIdle()
        vm.dispatch(ConversionAction.Start)
        testScheduler.advanceUntilIdle()

        assertFalse("旧产物应被清理（FR-014）", old.exists())
    }

    private class FakeExporter(private val canSave: Boolean = true) : PdfExporter {
        override fun canSaveToDownloads() = canSave
        override fun save(path: String) = canSave
    }
}