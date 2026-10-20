package com.foolproof.base.network

import com.foolproof.base.BuildConfig

/**
 * 网络客户端配置。全部字段都有可用的默认值，业务只需覆盖必要项。
 */
data class HttpConfig(
    /** 基础地址；为空时请求路径必须是完整 URL。 */
    val baseUrl: String = "",
    /** 每个请求都会附加的默认请求头。 */
    val defaultHeaders: Map<String, String> = emptyMap(),
    val connectTimeoutMillis: Long = 15_000L,
    val requestTimeoutMillis: Long = 30_000L,
    /** 仅在 debug 构建下才可能为 true——release 强制关闭日志。 */
    val loggingEnabled: Boolean = BuildConfig.DEBUG,
) {
    companion object {
        fun default(): HttpConfig = HttpConfig()
    }
}
