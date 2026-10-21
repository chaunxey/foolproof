package com.foolproof.features.duplicates

import com.foolproof.base.log.LogUtil
import com.foolproof.features.duplicates.domain.DeleteChannel
import com.foolproof.features.duplicates.domain.DeleteReport
import com.foolproof.features.duplicates.domain.DeleteResult
import com.foolproof.features.duplicates.domain.PhotoDeleter
import com.foolproof.features.duplicates.domain.PhotoItem
import com.foolproof.features.duplicates.domain.PhotoScanner
import com.foolproof.features.duplicates.domain.ScanEvent
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private fun item(id: String, hash: Long, size: Long = 1_000L) = PhotoItem(
        id = id,
        displayName = "$id.jpg",
        sizeBytes = size,
        width = 4000,
        height = 3000,
        hash = hash,
    )

    private class FakeScanner(
        private val items: List<PhotoItem>,
        private val skipped: Int = 0,
        private val fail: Boolean = false,
    ) : PhotoScanner {
        override fun scan(): Flow<ScanEvent> = flow {
            if (fail) {
                emit(ScanEvent.Failed(com.foolproof.features.duplicates.domain.ScanFailure.QUERY_FAILED))
                return@flow
            }
            emit(ScanEvent.Started(items.size))
            items.forEachIndexed { index, item ->
                emit(ScanEvent.Found(item))
                emit(ScanEvent.Progress(index + 1, items.size))
            }
            emit(ScanEvent.Finished(scanned = items.size, skipped = skipped))
        }
    }

    private class FakeDeleter(
        private val channel: DeleteChannel = DeleteChannel.SYSTEM_REQUEST,
        private val mode: Mode = Mode.NeedsConfirmation,
        private val survivorIds: Set<String> = emptySet(),
    ) : PhotoDeleter {

        enum class Mode { NeedsConfirmation, Completed }

        var requestedIds: List<String> = emptyList()

        override fun channel(): DeleteChannel = channel

        override fun setSafTree(treeUri: String?) = Unit

        override suspend fun delete(ids: List<String>): DeleteResult {
            requestedIds = ids
            return when (mode) {
                Mode.NeedsConfirmation -> DeleteResult.NeedsConfirmation(token = "token", requested = ids)
                Mode.Completed -> DeleteResult.Completed(
                    DeleteReport(requested = ids.size, deleted = ids.size, failed = 0),
                )
            }
        }

        override suspend fun stillExisting(ids: List<String>): Set<String> =
            ids.filter { it in survivorIds }.toSet()
    }

    private fun viewModel(
        scanner: PhotoScanner,
        deleter: PhotoDeleter = FakeDeleter(),
    ) = DuplicateViewModel(scanner = scanner, deleter = deleter, logger = LogUtil(enabled = false))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scan produces grouped duplicates with default selection`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeScanner(
                listOf(
                    item("big", 0L, size = 900L),
                    item("small", 0L, size = 100L),
                    item("unique", 0b1111111L),
                ),
            ),
        )

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.groups.size)
        assertEquals(2, state.groups.single().count)
        assertEquals(setOf("small"), state.selected)
        assertTrue(state.scan is ScanState.Done)
        assertEquals(3, (state.scan as ScanState.Done).scanned)
    }

    @Test
    fun `scan with no duplicates emits a message`() = runTest(dispatcher) {
        val vm = viewModel(FakeScanner(listOf(item("a", 0L), item("b", 0b1111111L))))
        val effects = mutableListOf<DuplicateEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.groups.isEmpty())
        assertTrue(
            effects.any {
                it is DuplicateEffect.Message && it.kind == DuplicateEffect.MessageKind.NO_DUPLICATES
            },
        )
        job.cancel()
    }

    @Test
    fun `empty gallery is reported separately`() = runTest(dispatcher) {
        val vm = viewModel(FakeScanner(emptyList()))
        val effects = mutableListOf<DuplicateEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()

        assertTrue(
            effects.any {
                it is DuplicateEffect.Message && it.kind == DuplicateEffect.MessageKind.EMPTY_GALLERY
            },
        )
        job.cancel()
    }

    @Test
    fun `scan failure is surfaced`() = runTest(dispatcher) {
        val vm = viewModel(FakeScanner(emptyList(), fail = true))

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.scan is ScanState.Failed)
    }

    @Test
    fun `cancel stops the scan`() = runTest(dispatcher) {
        val vm = viewModel(FakeScanner(List(10) { item("p$it", 0L) }))

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()
        vm.dispatch(DuplicateAction.CancelScan)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isScanning)
    }

    @Test
    fun `toggle updates selection and derived totals`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeScanner(
                listOf(
                    item("big", 0L, size = 900L),
                    item("small", 0L, size = 100L),
                ),
            ),
        )

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()
        assertEquals(setOf("small"), vm.uiState.value.selected)

        vm.dispatch(DuplicateAction.Toggle("big"))
        advanceUntilIdle()

        assertEquals(setOf("small", "big"), vm.uiState.value.selected)
        assertEquals(2, vm.uiState.value.selectedCount)
        assertEquals(1_000L, vm.uiState.value.selectedBytes)
    }

    @Test
    fun `group toggle selects and clears deletable items`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeScanner(
                listOf(
                    item("big", 0L, size = 900L),
                    item("small", 0L, size = 100L),
                ),
            ),
        )

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()
        val groupId = vm.uiState.value.groups.single().id

        vm.dispatch(DuplicateAction.ToggleGroup(groupId))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selected.isEmpty())

        vm.dispatch(DuplicateAction.ToggleGroup(groupId))
        advanceUntilIdle()
        assertEquals(setOf("small"), vm.uiState.value.selected)
    }

    @Test
    fun `permission denied forever emits a dedicated message`() = runTest(dispatcher) {
        val vm = viewModel(FakeScanner(emptyList()))
        val effects = mutableListOf<DuplicateEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(DuplicateAction.PermissionResolved(granted = false, permanentlyDenied = true))
        advanceUntilIdle()

        assertEquals(PermissionState.DENIED_FOREVER, vm.uiState.value.permission)
        assertTrue(
            effects.any {
                it is DuplicateEffect.Message &&
                    it.kind == DuplicateEffect.MessageKind.PERMISSION_DENIED_FOREVER
            },
        )
        job.cancel()
    }

    @Test
    fun `granted permission starts scanning automatically`() = runTest(dispatcher) {
        val vm = viewModel(FakeScanner(listOf(item("a", 0L), item("b", 0L))))

        vm.dispatch(DuplicateAction.PermissionResolved(granted = true, permanentlyDenied = false))
        advanceUntilIdle()

        assertEquals(PermissionState.GRANTED, vm.uiState.value.permission)
        assertEquals(1, vm.uiState.value.groups.size)
    }

    @Test
    fun `delete asks for confirmation before touching anything`() = runTest(dispatcher) {
        val deleter = FakeDeleter()
        val vm = viewModel(
            FakeScanner(listOf(item("big", 0L, 900L), item("small", 0L, 100L))),
            deleter,
        )
        val effects = mutableListOf<DuplicateEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()
        vm.dispatch(DuplicateAction.RequestDelete)
        advanceUntilIdle()

        assertTrue("必须先弹确认（SC-005）", effects.any { it is DuplicateEffect.ConfirmDelete })
        assertTrue("确认前不得调用删除", deleter.requestedIds.isEmpty())
        job.cancel()
    }

    @Test
    fun `system confirmed delete trusts the result even if trashed files are still readable`() =
        runTest(dispatcher) {
            // 刚移入回收站的文件短时间内仍可能被打开，不能因此报“部分失败”
            val deleter = FakeDeleter(
                mode = FakeDeleter.Mode.NeedsConfirmation,
                survivorIds = setOf("small", "extra"),
            )
            val vm = viewModel(
                FakeScanner(
                    listOf(
                        item("big", 0L, 900L),
                        item("small", 0L, 100L),
                        item("extra", 0L, 200L),
                    ),
                ),
                deleter,
            )
            val effects = mutableListOf<DuplicateEffect>()
            val job = launch { vm.effect.collect { effects += it } }
            advanceUntilIdle()

            vm.dispatch(DuplicateAction.RequestScan)
            advanceUntilIdle()
            vm.dispatch(DuplicateAction.RequestDelete)
            advanceUntilIdle()
            vm.dispatch(DuplicateAction.ConfirmDelete)
            advanceUntilIdle()

            val requested = deleter.requestedIds
            vm.dispatch(DuplicateAction.DeleteSettled(requested, systemConfirmed = true))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(requested.size, state.report?.deleted)
            assertEquals(0, state.report?.failed)
            assertFalse(
                effects.any {
                    it is DuplicateEffect.Message &&
                        it.kind == DuplicateEffect.MessageKind.DELETE_FAILED
                },
            )
            job.cancel()
        }

    @Test
    fun `system delete goes through confirmation then verification`() = runTest(dispatcher) {
        val deleter = FakeDeleter(mode = FakeDeleter.Mode.NeedsConfirmation)
        val vm = viewModel(
            FakeScanner(
                listOf(
                    item("big", 0L, 900L),
                    item("small", 0L, 100L),
                    item("extra", 0L, 200L),
                ),
            ),
            deleter,
        )
        val effects = mutableListOf<DuplicateEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()
        vm.dispatch(DuplicateAction.RequestDelete)
        advanceUntilIdle()
        vm.dispatch(DuplicateAction.ConfirmDelete)
        advanceUntilIdle()

        assertTrue(
            "30+ 通道应把系统删除请求交给 UI",
            effects.any { it is DuplicateEffect.LaunchSystemDelete },
        )

        val requested = deleter.requestedIds
        vm.dispatch(DuplicateAction.DeleteSettled(requested))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(requested.size, state.report?.requested)
        assertEquals(requested.size, state.report?.deleted)
        assertFalse(state.deleting)
        job.cancel()
    }

    @Test
    fun `items that survive remain in their group and are reported as failed`() = runTest(dispatcher) {
        val deleter = FakeDeleter(
            mode = FakeDeleter.Mode.NeedsConfirmation,
            survivorIds = setOf("extra"),
        )
        val vm = viewModel(
            FakeScanner(
                listOf(
                    item("big", 0L, 900L),
                    item("small", 0L, 100L),
                    item("extra", 0L, 200L),
                ),
            ),
            deleter,
        )
        advanceUntilIdle()

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()
        vm.dispatch(DuplicateAction.RequestDelete)
        advanceUntilIdle()
        vm.dispatch(DuplicateAction.ConfirmDelete)
        advanceUntilIdle()
        vm.dispatch(DuplicateAction.DeleteSettled(deleter.requestedIds))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(1, state.report?.failed)
        assertEquals(1, state.report?.deleted)
        assertTrue(state.groups.single().items.any { it.id == "extra" })
        assertFalse("small" in state.selected)
    }

    @Test
    fun `low api without saf tree cannot delete and asks for the tree`() = runTest(dispatcher) {
        val deleter = FakeDeleter(channel = DeleteChannel.NONE)
        val vm = viewModel(
            FakeScanner(listOf(item("big", 0L, 900L), item("small", 0L, 100L))),
            deleter,
        )
        val effects = mutableListOf<DuplicateEffect>()
        val job = launch { vm.effect.collect { effects += it } }
        advanceUntilIdle()

        vm.dispatch(DuplicateAction.RequestScan)
        advanceUntilIdle()
        vm.dispatch(DuplicateAction.RequestDelete)
        advanceUntilIdle()

        assertTrue(effects.any { it is DuplicateEffect.RequestSafTree })
        assertTrue(
            effects.any {
                it is DuplicateEffect.Message && it.kind == DuplicateEffect.MessageKind.SAF_REQUIRED
            },
        )
        assertTrue(deleter.requestedIds.isEmpty())
        job.cancel()
    }
}
