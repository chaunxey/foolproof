package com.foolproof.base.di

import com.foolproof.base.log.LogUtil
import com.foolproof.base.network.HttpConfig
import com.foolproof.base.network.NetworkClient
import com.foolproof.base.startKoinOnce
import io.ktor.client.HttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.KoinApplication
import org.koin.core.error.NoDefinitionFoundException
import org.koin.dsl.koinApplication

class BaseModuleTest {

    private fun startContainer(): KoinApplication =
        koinApplication { modules(baseModule) }

    @Test
    fun `network client is resolvable`() {
        val koin = startContainer().koin

        assertNotNull(koin.get<NetworkClient>())
    }

    @Test
    fun `logger is provided as a singleton`() {
        val koin = startContainer().koin

        assertNotNull(koin.get<LogUtil>())
        // 单例语义：两次解析必须是同一实例
        assertTrue(koin.get<LogUtil>() === koin.get<LogUtil>())
    }

    @Test
    fun `every declared type can be resolved without Android runtime`() {
        // 不用 Koin 的 verify()：它会反射 HttpClient 的构造参数（engine），
        // 对「Lambda 定义 + 第三方类型」会误报缺依赖。直接 resolve 更能反映真实可用性。
        val koin = startContainer().koin

        assertNotNull(koin.get<HttpConfig>())
        assertNotNull(koin.get<HttpClient>())
        assertNotNull(koin.get<LogUtil>())
        assertNotNull(koin.get<NetworkClient>())
    }

    @Test
    fun `resolving an undeclared type throws`() {
        val koin = startContainer().koin

        val thrown = runCatching { koin.get<Runnable>() }.exceptionOrNull()

        assertTrue("应抛出 Koin 未定义异常，实际: $thrown", thrown is NoDefinitionFoundException)
    }

    @Test
    fun `koin startup is idempotent`() {
        var starts = 0

        startKoinOnce(isStarted = { starts > 0 }) { starts++ }
        startKoinOnce(isStarted = { starts > 0 }) { starts++ }

        assertEquals(1, starts)
    }
}
