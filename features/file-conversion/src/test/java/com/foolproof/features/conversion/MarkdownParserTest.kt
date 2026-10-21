package com.foolproof.features.conversion

import com.foolproof.features.conversion.data.MarkdownParser
import com.foolproof.features.conversion.data.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `headings map to levels`() {
        val blocks = MarkdownParser.parse("# 一级\n## 二级\n### 三级")
        assertEquals(3, blocks.size)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertEquals(2, (blocks[1] as MdBlock.Heading).level)
        assertEquals(3, (blocks[2] as MdBlock.Heading).level)
    }

    @Test
    fun `deeper headings are capped at level three`() {
        val blocks = MarkdownParser.parse("#### 四级")
        assertEquals(3, (blocks[0] as MdBlock.Heading).level)
    }

    @Test
    fun `consecutive lines merge into one paragraph`() {
        val blocks = MarkdownParser.parse("第一行\n第二行\n\n另起一段")
        assertEquals(2, blocks.size)
        assertEquals("第一行 第二行", (blocks[0] as MdBlock.Paragraph).runs.joinToString("") { it.text })
    }

    @Test
    fun `unordered list keeps items`() {
        val blocks = MarkdownParser.parse("- 甲\n- 乙\n- 丙")
        val list = blocks[0] as MdBlock.ListBlock
        assertEquals(false, list.ordered)
        assertEquals(3, list.items.size)
    }

    @Test
    fun `ordered list is marked ordered`() {
        val blocks = MarkdownParser.parse("1. 第一\n2. 第二")
        val list = blocks[0] as MdBlock.ListBlock
        assertTrue(list.ordered)
        assertEquals(2, list.items.size)
    }

    @Test
    fun `switching list type starts a new block`() {
        val blocks = MarkdownParser.parse("- 无序\n1. 有序")
        assertEquals(2, blocks.size)
        assertEquals(false, (blocks[0] as MdBlock.ListBlock).ordered)
        assertTrue((blocks[1] as MdBlock.ListBlock).ordered)
    }

    @Test
    fun `code block preserves content and is not parsed as markdown`() {
        val blocks = MarkdownParser.parse("```\n# 不是标题\n- 不是列表\n```")
        val code = blocks[0] as MdBlock.CodeBlock
        assertEquals("# 不是标题\n- 不是列表", code.code)
    }

    @Test
    fun `divider is recognised`() {
        val blocks = MarkdownParser.parse("上面\n\n---\n\n下面")
        assertTrue(blocks.any { it is MdBlock.Divider })
    }

    @Test
    fun `inline bold italic and code become styled runs`() {
        val runs = MarkdownParser.parseInline("普通**粗**和*斜*和`码`")
        assertTrue(runs.any { it.style.bold && it.text == "粗" })
        assertTrue(runs.any { it.style.italic && it.text == "斜" })
        assertTrue(runs.any { it.style.code && it.text == "码" })
    }

    @Test
    fun `unclosed code block still keeps its content`() {
        val blocks = MarkdownParser.parse("```\n没闭合的代码")
        assertEquals(1, blocks.size)
        assertEquals("没闭合的代码", (blocks[0] as MdBlock.CodeBlock).code)
    }

    @Test
    fun `plain text yields one paragraph`() {
        val blocks = MarkdownParser.parse("就是一段普通的中文文本，没有任何标记。")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MdBlock.Paragraph)
    }

    @Test
    fun `empty input yields no blocks`() {
        assertTrue(MarkdownParser.parse("").isEmpty())
        assertTrue(MarkdownParser.parse("   \n\n").isEmpty())
    }
}
