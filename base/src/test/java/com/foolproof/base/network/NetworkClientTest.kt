package com.foolproof.base.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkClientTest {

    @Serializable
    data class Item(val id: Int, val name: String)

    private fun client(
        config: HttpConfig = HttpConfig(baseUrl = "https://api.test/"),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): NetworkClient {
        val httpClient = HttpClient(MockEngine) {
            engine { addHandler(handler) }
            applyHttpConfig(config)
        }
        return NetworkClient(httpClient, config.baseUrl)
    }

    @Test
    fun `200 with valid json yields Success`() = runTest {
        val network = client { request ->
            assertEquals("https://api.test/items/1", request.url.toString())
            respond(
                content = """{"id":1,"name":"photo"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = network.get<Item>("items/1")

        assertTrue("应为成功，实际 $result", result is ApiResult.Success)
        assertEquals(Item(1, "photo"), (result as ApiResult.Success).data)
    }

    @Test
    fun `401 maps to Unauthorized`() = runTest {
        val network = client { respondError(HttpStatusCode.Unauthorized) }

        val result = network.get<Item>("items/1")

        assertEquals(ApiResult.Failure(NetworkError.Unauthorized), result)
    }

    @Test
    fun `500 maps to Http error carrying status and body`() = runTest {
        val network = client { respondError(HttpStatusCode.InternalServerError, "upstream down") }

        val result = network.get<Item>("items/1")

        val error = (result as ApiResult.Failure).error
        assertTrue(error is NetworkError.Http)
        assertEquals(500, (error as NetworkError.Http).code)
        assertEquals("upstream down", error.body)
    }

    @Test
    fun `timeout maps to Timeout`() = runTest {
        val network = client { throw SocketTimeoutException("timeout") }

        val result = network.get<Item>("items/1")

        assertEquals(ApiResult.Failure(NetworkError.Timeout), result)
    }

    @Test
    fun `connection failure maps to NoNetwork`() = runTest {
        val network = client { throw IOException("connection reset") }

        val result = network.get<Item>("items/1")

        assertEquals(ApiResult.Failure(NetworkError.NoNetwork), result)
    }

    @Test
    fun `malformed json maps to Parse`() = runTest {
        val network = client {
            respond(
                content = "this-is-not-json",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = network.get<Item>("items/1")

        assertEquals(ApiResult.Failure(NetworkError.Parse), result)
    }

    @Test
    fun `configured default headers are attached to every request`() = runTest {
        var seenToken: String? = null
        val network = client(
            handler = { request ->
                seenToken = request.headers["X-Token"]
                respond(
                    content = """{"id":1,"name":"photo"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            },
            config = HttpConfig(
                baseUrl = "https://api.test/",
                defaultHeaders = mapOf("X-Token" to "abc123"),
            ),
        )

        network.get<Item>("items/1")

        assertEquals("abc123", seenToken)
    }

    @Test
    fun `post sends body and query parameters`() = runTest {
        var method: String? = null
        var query: String? = null
        var body: String? = null
        val network = client { request ->
            method = request.method.value
            query = request.url.parameters["page"]
            body = request.body.toString()
            respond(
                content = """{"id":2,"name":"posted"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        val result = network.post<Item>("items", body = mapOf("name" to "posted"), query = mapOf("page" to "1"))

        assertEquals("POST", method)
        assertEquals("1", query)
        assertTrue(result is ApiResult.Success)
        assertTrue("请求体应包含 name 字段，实际: $body", body?.contains("name") == true)
    }

    @Test
    fun `absolute url bypasses base url`() = runTest {
        var url: String? = null
        val network = client(handler = { request ->
            url = request.url.toString()
            respond(
                content = """{"id":1,"name":"x"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        })

        network.get<Item>("https://other.test/ping")

        assertEquals("https://other.test/ping", url)
    }
}
