package com.foolproof.base.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import kotlin.coroutines.cancellation.CancellationException

/**
 * 对外唯一的网络出口（宪法 §5 / FR-012）：只提供 [get] 与 [post]。
 *
 * 任何失败都以 [ApiResult.Failure] 返回，**不向调用方抛异常**。
 */
class NetworkClient @PublishedApi internal constructor(
    @PublishedApi internal val client: HttpClient,
    private val baseUrl: String = "",
) {

    suspend inline fun <reified T> get(
        path: String,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> = execute(typeInfo<T>(), HttpMethod.Get, path, query, body = null, headers = headers)

    suspend inline fun <reified T> post(
        path: String,
        body: Any? = null,
        query: Map<String, String> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> = execute(typeInfo<T>(), HttpMethod.Post, path, query, body = body, headers = headers)

    @PublishedApi
    internal suspend fun <T> execute(
        typeInfo: TypeInfo,
        method: HttpMethod,
        path: String,
        query: Map<String, String>,
        body: Any?,
        headers: Map<String, String>,
    ): ApiResult<T> = try {
        val response = client.request(buildUrl(path)) {
            this.method = method
            query.forEach { (name, value) -> parameter(name, value) }
            headers.forEach { (name, value) -> header(name, value) }
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        when {
            response.status == HttpStatusCode.Unauthorized ->
                ApiResult.Failure(NetworkError.Unauthorized)

            !response.status.isSuccess() ->
                ApiResult.Failure(
                    NetworkError.Http(
                        code = response.status.value,
                        body = runCatching { response.bodyAsText() }.getOrNull(),
                    )
                )

            else -> ApiResult.Success(response.body(typeInfo))
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        ApiResult.Failure(ErrorMapper.map(throwable))
    }

    @PublishedApi
    internal fun buildUrl(path: String): String = when {
        path.startsWith("http://") || path.startsWith("https://") -> path
        baseUrl.isBlank() -> path
        else -> baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }
}
