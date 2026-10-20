package com.foolproof.base.mvi

/**
 * 不可变状态快照。实现类必须是 data class，且不包含任何可变集合或一次性事件。
 */
interface UiState

/**
 * 一次性副作用（导航、弹窗、Toast 等）。实现类必须是 sealed 类型，
 * 且不得放入 [UiState]——否则页面重建时会被重复消费。
 */
interface UiEffect

/**
 * UI 发往 ViewModel 的意图。ViewModel 只通过 `dispatch(action)` 接收。
 */
interface UiAction
