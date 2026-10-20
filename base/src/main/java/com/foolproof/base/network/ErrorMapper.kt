package com.foolproof.base.network

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.serialization.ContentConvertException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.serialization.SerializationException

/**
 * 把底层异常映射为业务可区分的 [NetworkError]。
 *
 * 判定顺序很重要：Ktor 的超时异常继承自 [IOException]，必须先判超时。
 */
object ErrorMapper {

    fun map(throwable: Throwable): NetworkError = when {
        throwable is HttpRequestTimeoutException -> NetworkError.Timeout
        throwable is ConnectTimeoutException -> NetworkError.Timeout
        throwable is SocketTimeoutException -> NetworkError.Timeout
        throwable is ContentConvertException -> NetworkError.Parse
        throwable is SerializationException -> NetworkError.Parse
        throwable is IOException -> NetworkError.NoNetwork
        else -> NetworkError.Unknown(throwable)
    }
}
