package com.foolproof.base.mvi

import com.foolproof.base.log.LogUtil
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private data class CounterState(val count: Int = 0) : UiState

    private sealed interface CounterEffect : UiEffect {
        data object Emitted : CounterEffect
    }

    private sealed interface CounterAction : UiAction {
        data object Increment : CounterAction
        data object Emit : CounterAction
        data object Fail : CounterAction
        data object Cancel : CounterAction
    }

    private class CounterViewModel(logger: LogUtil) :
        BaseViewModel<CounterState, CounterEffect>(CounterState(), logger) {

        val errors = mutableListOf<Throwable>()

        override fun onError(error: Throwable) {
            errors += error
        }

        override fun onAction(action: UiAction) {
            when (action) {
                CounterAction.Increment -> updateState { state ->
                    state.copy(count = state.count + 1)
                }

                CounterAction.Emit -> sendEffect(CounterEffect.Emitted)
                CounterAction.Fail -> launch { error("boom") }
                CounterAction.Cancel -> launch { throw CancellationException("cancelled") }
                else -> Unit
            }
        }

        val snapshot: CounterState get() = currentState
    }

    /** 不覆写 onError，用于验证默认实现会写日志。 */
    private class DefaultErrorViewModel(logger: LogUtil) :
        BaseViewModel<CounterState, CounterEffect>(CounterState(), logger) {

        override fun onAction(action: UiAction) {
            launch { error("boom") }
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CounterViewModel(LogUtil(enabled = false))

    @Test
    fun `dispatch updates state`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.dispatch(CounterAction.Increment)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.count)
    }

    @Test
    fun `effect is consumed exactly once by competing collectors`() = runTest(dispatcher) {
        val viewModel = viewModel()
        val received = mutableListOf<CounterEffect>()

        val first = launch { viewModel.effect.collect { received += it } }
        val second = launch { viewModel.effect.collect { received += it } }
        advanceUntilIdle()

        viewModel.dispatch(CounterAction.Emit)
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertTrue(received.single() is CounterEffect.Emitted)

        first.cancel()
        second.cancel()
    }

    @Test
    fun `state survives re-collection with the latest value`() = runTest(dispatcher) {
        val viewModel = viewModel()

        repeat(3) { viewModel.dispatch(CounterAction.Increment) }
        advanceUntilIdle()

        var latest: CounterState? = null
        val job = launch { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(3, latest?.count)
        assertEquals(3, viewModel.snapshot.count)
    }

    @Test
    fun `exception inside launch is routed to onError and state stays intact`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.dispatch(CounterAction.Fail)
            advanceUntilIdle()

            assertEquals(1, viewModel.errors.size)
            assertTrue(viewModel.errors.single() is IllegalStateException)
            assertEquals(0, viewModel.uiState.value.count)
        }

    @Test
    fun `cancellation is not reported as an error`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.dispatch(CounterAction.Cancel)
        advanceUntilIdle()

        assertTrue(viewModel.errors.isEmpty())
    }

    @Test
    fun `default onError writes to the logger`() = runTest(dispatcher) {
        val lines = mutableListOf<String>()
        val logger = LogUtil(enabled = true).also { it.sink = { line -> lines += line } }
        val viewModel = DefaultErrorViewModel(logger)

        viewModel.dispatch(CounterAction.Increment)
        advanceUntilIdle()

        assertTrue("默认异常处理应写日志，实际: $lines", lines.any { it.contains("unhandled error") })
    }
}
