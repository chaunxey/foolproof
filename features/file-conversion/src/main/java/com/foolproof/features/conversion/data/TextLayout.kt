package com.foolproof.features.conversion.data

/** 排版后的一行（按样式分段）。 */
data class TextLine(val runs: List<MdRun>) {
    val plainText: String get() = runs.joinToString("") { it.text }
}

/**
 * 文本折行与分页（plan D5）。
 *
 * 刻意把"字宽测量"抽成构造参数：Android 上用 `Paint.measureText`，
 * 单测里用等宽或按字符数估算的假实现——这样折行/分页逻辑可以被 JVM 单测完整覆盖。
 */
class TextLayout(private val measure: (String, MdStyle) -> Float) {

    /** 把一串 run 折成若干行，保证每行宽度 ≤ [maxWidth]。 */
    fun wrap(runs: List<MdRun>, maxWidth: Float): List<TextLine> {
        if (maxWidth <= 0f) return emptyList()

        val lines = mutableListOf<TextLine>()
        var current = mutableListOf<MdRun>()
        var width = 0f

        fun pushLine() {
            if (current.isNotEmpty()) {
                lines += TextLine(current.toList())
                current = mutableListOf()
                width = 0f
            }
        }

        for (run in runs) {
            for (token in tokenize(run.text)) {
                for (piece in fit(token, run.style, maxWidth)) {
                    val pieceWidth = measure(piece, run.style)
                    if (width + pieceWidth > maxWidth && current.isNotEmpty()) pushLine()
                    current += MdRun(piece, run.style)
                    width += pieceWidth
                }
            }
        }
        pushLine()
        return lines
    }

    /**
     * 把 token 切成不超过 [maxWidth] 的片段。
     *
     * 正常情况下原样返回；只有当单个 token（如超长 URL）比整行还宽时才按字符切，
     * 保证 spec US2/AC3「超长无空格串必须强制断行、不溢出页面」。
     */
    private fun fit(token: String, style: MdStyle, maxWidth: Float): List<String> {
        if (token.isEmpty()) return emptyList()
        if (measure(token, style) <= maxWidth) return listOf(token)

        val pieces = mutableListOf<String>()
        val buffer = StringBuilder()
        for (ch in token) {
            val candidate = buffer.toString() + ch
            if (buffer.isNotEmpty() && measure(candidate, style) > maxWidth) {
                pieces += buffer.toString()
                buffer.clear()
            }
            buffer.append(ch)
        }
        if (buffer.isNotEmpty()) pieces += buffer.toString()
        return pieces
    }

    /**
     * 按行高分页：累加高度，超出 [contentHeight] 前换页。
     *
     * 返回每页的行列表；空输入返回空列表（调用方据此判定"没有可转换的内容"）。
     */
    fun paginate(
        lines: List<TextLine>,
        contentHeight: Float,
        lineHeightOf: (TextLine) -> Float,
    ): List<List<TextLine>> {
        if (lines.isEmpty() || contentHeight <= 0f) return emptyList()

        val pages = mutableListOf<List<TextLine>>()
        var page = mutableListOf<TextLine>()
        var used = 0f

        for (line in lines) {
            val h = lineHeightOf(line)
            if (page.isNotEmpty() && used + h > contentHeight) {
                pages += page
                page = mutableListOf()
                used = 0f
            }
            page += line
            used += h
        }
        if (page.isNotEmpty()) pages += page
        return pages
    }
}

/** 把文本切成"最小不可再分单元"：CJK 逐字、拉丁按词（空格跟随前词）。 */
internal fun tokenize(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val tokens = mutableListOf<String>()
    val buffer = StringBuilder()

    fun flushBuffer() {
        if (buffer.isNotEmpty()) {
            tokens += buffer.toString()
            buffer.clear()
        }
    }

    for (ch in text) {
        when {
            isCjk(ch) -> {
                flushBuffer()
                tokens += ch.toString()
            }

            ch == ' ' || ch == '\t' -> {
                buffer.append(' ')
                flushBuffer()
            }

            else -> buffer.append(ch)
        }
    }
    flushBuffer()
    return tokens
}

private fun isCjk(ch: Char): Boolean {
    val code = ch.code
    return code in 0x4E00..0x9FFF ||      // 基本汉字
        code in 0x3400..0x4DBF ||         // 扩展 A
        code in 0x3000..0x303F ||         // 中日韩标点
        code in 0xFF00..0xFFEF            // 全角字符
}
