package com.foolproof.base.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全项目 ViewModel 的统一契约（宪法 §4）。
 *
 * - 状态：以只读 [StateFlow] 暴露，可变容器为 private，仅能经受保护的 [updateState] 修改；
 * - 副作用：走 [Channel] + [receiveAsFlow]，保证同一事件只被一个收集者消费一次；
 * - 入口：UI 只能调用 [dispatch]，不允许直接改状态。
 *
 * @param S 不可变状态
 * @param E 一次性副作用
 */
abstract class BaseViewModel<S : UiState, E : UiEffect>(initialState: S) : ViewModel() {

    private val _uiState = MutableStateFlow(initialState)

    /** 只读状态流。页面重建后重新收集仍能拿到最新值。 */
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    private val _effect = Channel<E>(Channel.BUFFERED)

    /** 一次性副作用流。多收集者之间是竞争消费，不会重复触发。 */
    val effect: Flow<E> = _effect.receiveAsFlow()

    /** 当前状态快照，等价于 `uiState.value`。 */
    protected val currentState: S
        get() = _uiState.value

    /** UI 的唯一入口。 */
    fun dispatch(action: UiAction) {
        onAction(action)
    }

    /** 子类在此处理 action。 */
    protected abstract fun onAction(action: UiAction)

    /** 子类唯一可用的状态修改方式。 */
    protected fun updateState(reducer: (S) -> S) {
        _uiState.update(reducer)
    }

    /** 发送一次性副作用。非阻塞：缓冲满时丢弃并返回 false，不影响调用方。 */
    protected fun sendEffect(effect: E): Boolean = _effect.trySend(effect).isSuccess

    /** 在 ViewModel 作用域内启动协程，异常交由调用方自行处理。 */
    protected fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
