package com.foolproof.base.network

import com.foolproof.base.log.LogUtil
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * 构造全局唯一的 [HttpClient]。业务代码不得自行创建客户端（宪法 §5 / FR-016）。
 */
fun createHttpClient(config: HttpConfig): HttpClient = HttpClient(OkHttp) {
    applyHttpConfig(config)
}

/**
 * 把 [HttpConfig] 应用到任意引擎上。抽出为扩展函数是为了让单元测试能用 MockEngine
 * 验证「默认请求头 / 超时 / 日志开关」确实生效。
 */
internal fun <T : HttpClientEngineConfig> HttpClientConfig<T>.applyHttpConfig(config: HttpConfig) {
    // 非 2xx 不抛异常，交给 NetworkClient 判定，便于统一映射。
    expectSuccess = false

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                explicitNulls = false
            }
        )
    }

    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeoutMillis
        requestTimeoutMillis = config.requestTimeoutMillis
    }

    if (config.defaultHeaders.isNotEmpty()) {
        defaultRequest {
            config.defaultHeaders.forEach { (name, value) -> header(name, value) }
        }
    }

    if (config.loggingEnabled) {
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    LogUtil.d { message }
                }
            }
        }
    }
}
