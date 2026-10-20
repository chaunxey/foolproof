package com.foolproof.base.network

/**
 * 网络请求的统一结果。调用方永远拿到它，而不是异常。
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val data: T) : ApiResult<T>

    data class Failure(val error: NetworkError) : ApiResult<Nothing>

    /** 仅在成功时取数据，失败返回 null。 */
    fun dataOrNull(): T? = (this as? Success)?.data

    val isSuccess: Boolean
        get() = this is Success
}

/** 成功时取数据，否则用 [fallback]。 */
inline fun <T> ApiResult<T>.getOrElse(fallback: (NetworkError) -> T): T = when (this) {
    is ApiResult.Success -> data
    is ApiResult.Failure -> fallback(error)
}
