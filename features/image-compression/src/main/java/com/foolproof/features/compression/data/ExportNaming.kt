package com.foolproof.features.compression.data

import com.foolproof.features.compression.domain.CompressionOption
import java.io.File

/**
 * 输出文件命名规则。纯 `java.io.File` 运算，可在 JVM 单测里覆盖重名与特殊字符。
 */
object ExportNaming {

    private const val FALLBACK_NAME = "image"
    private const val MAX_ATTEMPTS = 1000

    /**
     * 生成不含目录的文件名：`{原名}_q{质量}.{扩展名}`。
     * 原名去掉扩展名与路径分隔符，空名回退为 `image`。
     */
    fun fileName(displayName: String, option: CompressionOption): String {
        val base = sanitize(displayName)
        return "${base}_q${option.quality}.${option.extension}"
    }

    /**
     * 在 [directory] 下选一个不冲突的文件：重名时依次追加 `_1`、`_2`…
     */
    fun uniqueFile(directory: File, displayName: String, option: CompressionOption): File {
        val base = sanitize(displayName)
        val ext = option.extension
        var candidate = File(directory, "${base}_q${option.quality}.$ext")
        var index = 1
        while (candidate.exists() && index < MAX_ATTEMPTS) {
            candidate = File(directory, "${base}_q${option.quality}_$index.$ext")
            index++
        }
        return candidate
    }

    private fun sanitize(displayName: String): String {
        val withoutPath = displayName.substringAfterLast('/').substringAfterLast('\\')
        val withoutExtension = withoutPath.substringBeforeLast('.', withoutPath)
        val cleaned = withoutExtension.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        return cleaned.ifBlank { FALLBACK_NAME }
    }
}
