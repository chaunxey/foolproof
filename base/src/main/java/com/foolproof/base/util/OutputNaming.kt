package com.foolproof.base.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 用户可见产物的统一命名：`Foolproof_{TYPE}_{ACTION}_{yyyyMMdd_HHmmss}[_{NN}].{ext}`。
 *
 * 约束：只含 ASCII、可排序、可读；批量/同秒冲突用两位序号。
 */
object OutputNaming {

    private const val PREFIX = "Foolproof"
    private const val TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"

    enum class MediaType(val token: String) {
        IMAGE("IMG"),
        VIDEO("VID"),
        PDF("PDF"),
    }

    enum class Action(val token: String) {
        COMPRESS("COMP"),
        MERGE("MERGE"),
        SPLIT("SPLIT"),
        ROTATE("ROTATE"),
        CONVERT("CONV"),
    }

    fun fileName(
        type: MediaType,
        action: Action,
        extension: String,
        timestampMs: Long = System.currentTimeMillis(),
        index: Int? = null,
        timeZone: TimeZone = TimeZone.getDefault(),
    ): String {
        require(extension.isNotBlank()) { "extension required" }
        val formatter = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).apply {
            this.timeZone = timeZone
        }
        val stamp = formatter.format(Date(timestampMs))
        val suffix = index?.let { "_%02d".format(Locale.US, it) }.orEmpty()
        val ext = extension.lowercase(Locale.US)
        return "${PREFIX}_${type.token}_${action.token}_${stamp}${suffix}.$ext"
    }

    /** 在扩展名前追加两位序号：`..._161500.jpg` → `..._161500_02.jpg`。 */
    fun numbered(name: String, index: Int): String {
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        return "${base}_%02d".format(Locale.US, index) + extension
    }
}
