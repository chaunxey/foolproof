package com.foolproof.base.mvi

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
    }

    private class CounterViewModel :
        BaseViewModel<CounterState, CounterEffect>(CounterState()) {

        override fun onAction(action: UiAction) {
            when (action) {
                CounterAction.Increment -> updateState { state ->
                    state.copy(count = state.count + 1)
                }

                CounterAction.Emit -> sendEffect(CounterEffect.Emitted)
                else -> Unit
            }
        }

        val snapshot: CounterState get() = currentState
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
    fun `dispatch updates state`() = runTest(dispatcher) {
        val viewModel = CounterViewModel()

        viewModel.dispatch(CounterAction.Increment)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.count)
    }

    @Test
    fun `effect is consumed exactly once by competing collectors`() = runTest(dispatcher) {
        val viewModel = CounterViewModel()
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
        val viewModel = CounterViewModel()

        repeat(3) { viewModel.dispatch(CounterAction.Increment) }
        advanceUntilIdle()

        var latest: CounterState? = null
        val job = launch { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()
        job.cancel()

        assertEquals(3, latest?.count)
        assertEquals(3, viewModel.snapshot.count)
    }
}
