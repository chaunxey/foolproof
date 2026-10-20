package com.foolproof.base.log

import com.foolproof.base.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogUtilTest {

    private val lines = mutableListOf<String>()

    @Before
    fun setUp() {
        lines.clear()
        LogUtil.sink = { line -> lines += line }
        LogUtil.enabled = true
    }

    @After
    fun tearDown() {
        LogUtil.sink = {}
        LogUtil.enabled = BuildConfig.DEBUG
    }

    @Test
    fun `output contains file name line number and message`() {
        LogUtil.d { "hello world" }

        val line = lines.single()
        assertTrue("缺少文件名: $line", line.contains("LogUtilTest.kt:"))
        assertTrue("缺少消息: $line", line.contains("hello world"))
    }

    @Test
    fun `disabled log writes nothing`() {
        LogUtil.enabled = false

        LogUtil.d { "should not appear" }
        LogUtil.e { "should not appear either" }

        assertTrue(lines.isEmpty())
    }

    @Test
    fun `disabled log does not evaluate the message lambda`() {
        LogUtil.enabled = false
        var evaluations = 0

        LogUtil.d {
            evaluations++
            "expensive ${'$'}{1 + 1}"
        }

        assertEquals(0, evaluations)
    }

    @Test
    fun `api key and authorization header are masked`() {
        val masked = LogUtil.sanitize(
            "key=sk-abcdef123456 header=Authorization: Bearer abc.def"
        )

        assertFalse("密钥未脱敏: $masked", masked.contains("sk-abcdef123456"))
        assertTrue("未保留尾部提示: $masked", masked.contains("sk-***3456"))
        assertFalse("Authorization 未脱敏: $masked", masked.contains("Bearer abc.def"))
        assertTrue(masked.contains("Authorization: ***"))
    }

    @Test
    fun `enabled cannot be turned on in release builds`() {
        // release 下 enabled 恒为 false；此处验证 setter 与 BuildConfig.DEBUG 的组合逻辑。
        LogUtil.enabled = true
        assertEquals(BuildConfig.DEBUG, LogUtil.enabled)
    }
}
