package com.foolproof.base.di

import com.foolproof.base.network.HttpConfig
import com.foolproof.base.network.NetworkClient
import com.foolproof.base.network.createHttpClient
import io.ktor.client.HttpClient
import org.koin.dsl.module

/**
 * base 模块的依赖声明（宪法 §5）。
 *
 * 只登记「需要外部配置、容易替换、需要统一生命周期、需要对实例数量控制、需要便于测试」的对象，
 * 普通工具类与纯函数不登记。
 */
val baseModule = module {

    single { HttpConfig.default() }

    single<HttpClient> { createHttpClient(get()) }

    single { NetworkClient(client = get(), baseUrl = get<HttpConfig>().baseUrl) }
}
