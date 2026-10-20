package com.foolproof.base.network

/**
 * 网络错误分类。业务层只需要对这几种情况做分支，不需要理解底层异常类型。
 */
sealed interface NetworkError {

    /** 无网络、DNS 失败、连接被拒绝等。 */
    data object NoNetwork : NetworkError

    /** 连接或请求超时。 */
    data object Timeout : NetworkError

    /** HTTP 401。 */
    data object Unauthorized : NetworkError

    /** 其它 4xx / 5xx。 */
    data class Http(val code: Int, val body: String? = null) : NetworkError

    /** 响应无法按预期格式解析。 */
    data object Parse : NetworkError

    /** 未分类异常，保留原始原因便于排查。 */
    data class Unknown(val cause: Throwable? = null) : NetworkError

    /** 面向用户的简短说明，业务层可直接展示。 */
    fun describe(): String = when (this) {
        NoNetwork -> "网络不可用"
        Timeout -> "请求超时"
        Unauthorized -> "凭证无效或已过期"
        is Http -> "服务异常（HTTP $code）"
        Parse -> "响应格式错误"
        is Unknown -> "未知错误"
    }
}
