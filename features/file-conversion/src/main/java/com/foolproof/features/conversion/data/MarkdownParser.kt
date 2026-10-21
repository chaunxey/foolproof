package com.foolproof.features.conversion.data

/** Markdown 的行内样式。 */
data class MdStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
)

/** 一段同格式的文本。 */
data class MdRun(val text: String, val style: MdStyle = MdStyle())

/** 解析后的块级元素（只支持 spec FR-003 列举的子集）。 */
sealed interface MdBlock {
    data class Heading(val level: Int, val runs: List<MdRun>) : MdBlock
    data class Paragraph(val runs: List<MdRun>) : MdBlock
    data class ListBlock(val ordered: Boolean, val items: List<List<MdRun>>) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
    data object Divider : MdBlock
}

/**
 * 极简 Markdown 解析器（plan D4：不引库）。
 *
 * 支持：`#`~`###` 标题、段落、`-`/`*`/`1.` 列表、`**粗体**`、`*斜体*`、
 * `` `行内代码` ``、三反引号代码块、`---` 分隔线。其余语法按纯文本处理。
 *
 * 纯 Kotlin、无 Android 依赖 → JVM 单测直接覆盖。
 */
object MarkdownParser {

    fun parse(markdown: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')

        val paragraph = mutableListOf<String>()
        val listItems = mutableListOf<List<MdRun>>()
        var listOrdered = false
        val code = StringBuilder()
        var inCode = false

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(parseInline(paragraph.joinToString(" ")))
                paragraph.clear()
            }
        }

        fun flushList() {
            if (listItems.isNotEmpty()) {
                blocks += MdBlock.ListBlock(listOrdered, listItems.toList())
                listItems.clear()
            }
        }

        for (raw in lines) {
            val line = raw.trimEnd()

            // 代码块边界
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    blocks += MdBlock.CodeBlock(code.toString().trimEnd('\n'))
                    code.clear()
                    inCode = false
                } else {
                    flushParagraph()
                    flushList()
                    inCode = true
                }
                continue
            }
            if (inCode) {
                code.append(line).append('\n')
                continue
            }

            when {
                line.isBlank() -> {
                    flushParagraph()
                    flushList()
                }

                line.trim() == "---" || line.trim() == "***" -> {
                    flushParagraph()
                    flushList()
                    blocks += MdBlock.Divider
                }

                HEADING.matches(line) -> {
                    flushParagraph()
                    flushList()
                    val level = HEADING.find(line)!!.groupValues[1].length.coerceAtMost(3)
                    val text = line.trimStart('#').trim()
                    blocks += MdBlock.Heading(level, parseInline(text))
                }

                UNORDERED.matches(line) -> {
                    flushParagraph()
                    if (listItems.isNotEmpty() && listOrdered) flushList()
                    listOrdered = false
                    listItems += parseInline(UNORDERED.replace(line, "").trim())
                }

                ORDERED.matches(line) -> {
                    flushParagraph()
                    if (listItems.isNotEmpty() && !listOrdered) flushList()
                    listOrdered = true
                    listItems += parseInline(ORDERED.replace(line, "").trim())
                }

                else -> {
                    flushList()
                    paragraph += line.trim()
                }
            }
        }

        // 结尾未闭合的代码块也要收进来，避免内容丢失
        if (inCode && code.isNotEmpty()) blocks += MdBlock.CodeBlock(code.toString().trimEnd('\n'))
        flushParagraph()
        flushList()
        return blocks
    }

    /** 行内解析：`**粗**`、`*斜*`、`` `码` ``。 */
    fun parseInline(text: String): List<MdRun> {
        val runs = mutableListOf<MdRun>()
        val buffer = StringBuilder()

        fun flush() {
            if (buffer.isNotEmpty()) {
                runs += MdRun(buffer.toString())
                buffer.clear()
            }
        }

        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        flush()
                        runs += MdRun(text.substring(i + 2, end), MdStyle(bold = true))
                        i = end + 2
                    } else {
                        buffer.append(text[i]); i++
                    }
                }

                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        flush()
                        runs += MdRun(text.substring(i + 1, end), MdStyle(code = true))
                        i = end + 1
                    } else {
                        buffer.append(text[i]); i++
                    }
                }

                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end > i + 1) {
                        flush()
                        runs += MdRun(text.substring(i + 1, end), MdStyle(italic = true))
                        i = end + 1
                    } else {
                        buffer.append(text[i]); i++
                    }
                }

                else -> {
                    buffer.append(text[i]); i++
                }
            }
        }
        flush()
        return runs
    }

    private val HEADING = Regex("^(#{1,6})\\s+.*")
    private val UNORDERED = Regex("^\\s*[-*+]\\s+.*")
    private val ORDERED = Regex("^\\s*\\d+[.)]\\s+.*")
}
