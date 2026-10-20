package com.foolproof.base

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import com.foolproof.base.di.baseModule

/**
 * 全应用 Application 基类：负责装配 Koin 容器（宪法 §3 / FR-004）。
 *
 * 子类（如 `:app` 的 FoolproofApp）覆写 [koinModules] 追加自己的模块即可。
 */
open class BaseApplication : Application() {

    /** 本应用要装配的全部 Koin 模块。子类可覆写并追加。 */
    open fun koinModules(): List<Module> = listOf(baseModule)

    override fun onCreate() {
        super.onCreate()
        startKoinIfNeeded()
    }

    /** 幂等启动：容器已存在时直接跳过（FR-005，便于测试与多入口）。 */
    fun startKoinIfNeeded() {
        startKoinOnce(isStarted = { GlobalContext.getOrNull() != null }) {
            startKoin {
                androidContext(this@BaseApplication)
                modules(koinModules())
            }
        }
    }
}

/** 抽出为独立函数，使「幂等」逻辑无需 Android 运行时即可单测。 */
internal inline fun startKoinOnce(isStarted: () -> Boolean, start: () -> Unit) {
    if (!isStarted()) start()
}
