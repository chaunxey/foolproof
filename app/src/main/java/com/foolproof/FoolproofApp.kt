package com.foolproof

import com.foolproof.base.BaseApplication
import org.koin.core.module.Module

/**
 * 应用入口。base 模块负责 Koin 容器启动，这里只负责追加 app 自己的模块。
 */
class FoolproofApp : BaseApplication() {

    override fun koinModules(): List<Module> = super.koinModules()
}
