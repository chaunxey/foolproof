package com.foolproof.base.di

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.base.mvi.UiEffect
import com.foolproof.base.mvi.UiState
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * 验证宪法 §5 的 ViewModel 注入约定：业务侧用 `viewModel { }` 注册 [BaseViewModel] 子类，
 * 容器即可解析。本测试锁住这个范式，避免后续模块各写一套。
 */
class ViewModelRegistrationTest {

    private data class DemoState(val value: Int = 0) : UiState

    private sealed interface DemoEffect : UiEffect

    private class DemoViewModel :
        BaseViewModel<DemoState, DemoEffect>(DemoState(), LogUtil(enabled = false)) {
        override fun onAction(action: UiAction) = Unit
    }

    @Test
    fun `viewmodel declared with koin viewModel dsl is resolvable`() {
        val koin = koinApplication {
            modules(
                module {
                    viewModel { DemoViewModel() }
                }
            )
        }.koin

        assertNotNull(koin.get<DemoViewModel>())
    }
}
