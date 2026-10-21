package com.foolproof.features.conversion

import com.foolproof.features.conversion.data.MdRun
import com.foolproof.features.conversion.data.MdStyle
import com.foolproof.features.conversion.data.TextLayout
import com.foolproof.features.conversion.data.tokenize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextLayoutTest {

    /** 假测量：每个字符固定 10 宽（等宽字体），便于精确断言。 */
    private val layout = TextLayout { text, _ -> text.length * 10f }

    @Test
    fun `wraps english by words`() {
        val lines = layout.wrap(listOf(MdRun("hello world foo bar")), maxWidth = 120f)
        // "hello " (6) + "world " (6) = 120 → 刚好放下；"foo bar" 换行
        assertTrue(lines.size >= 2)
        assertTrue(lines.all { it.plainText.length * 10 <= 120 })
    }

    @Test
    fun `wraps chinese per character`() {
        val lines = layout.wrap(listOf(MdRun("一二三四五六七八九十")), maxWidth = 50f)
        assertEquals(2, lines.size)
        assertEquals("一二三四五", lines[0].plainText)
    }

    @Test
    fun `long unbreakable token is force-split`() {
        val url = "https://example.com/" + "a".repeat(40)
        val lines = layout.wrap(listOf(MdRun(url)), maxWidth = 200f)
        assertTrue("超长 token 必须被强制断行", lines.size > 1)
        assertTrue(lines.all { it.plainText.length * 10 <= 200 })
    }

    @Test
    fun `no line exceeds the page width`() {
        val text = "中文混排 with English words " + "x".repeat(100)
        val lines = layout.wrap(listOf(MdRun(text)), maxWidth = 300f)
        assertTrue(lines.all { it.plainText.length * 10 <= 300 })
    }

    @Test
    fun `paginate splits when content height is exceeded`() {
        val lines = (1..10).map { MdRun("行$it") }.let { runs ->
            runs.map { com.foolproof.features.conversion.data.TextLine(listOf(it)) }
        }
        val pages = layout.paginate(lines, contentHeight = 30f) { 10f }
        assertEquals(4, pages.size) // 3 行/页，10 行 → 3+3+3+1
        assertEquals(3, pages[0].size)
        assertEquals(1, pages[3].size)
    }

    @Test
    fun `paginate with no lines yields no pages`() {
        assertTrue(layout.paginate(emptyList(), 100f) { 10f }.isEmpty())
    }

    @Test
    fun `styles are preserved per run`() {
        val lines = layout.wrap(
            listOf(MdRun("普通"), MdRun("加粗", MdStyle(bold = true))),
            maxWidth = 1000f,
        )
        assertEquals(1, lines.size)
        assertTrue(lines[0].runs.any { it.style.bold })
    }

    @Test
    fun `tokenize splits cjk per char and keeps words`() {
        assertEquals(listOf("中", "文"), tokenize("中文"))
        assertEquals(listOf("abc "), tokenize("abc "))
        assertEquals(listOf("中", "abc"), tokenize("中abc"))
    }
}
