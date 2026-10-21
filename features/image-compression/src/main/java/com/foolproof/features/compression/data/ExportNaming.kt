package com.foolproof.features.compression.data

import com.foolproof.base.util.OutputNaming
import com.foolproof.features.compression.domain.CompressionOption
import java.io.File

/**
 * 图片产物命名：统一走 [OutputNaming] 的 `Foolproof_IMG_COMP_...`。
 * 目录内重名时追加 `_02`、`_03`。
 */
object ExportNaming {

    private const val MAX_ATTEMPTS = 1000

    fun fileName(
        option: CompressionOption,
        timestampMs: Long = System.currentTimeMillis(),
    ): String = OutputNaming.fileName(
        type = OutputNaming.MediaType.IMAGE,
        action = OutputNaming.Action.COMPRESS,
        extension = option.extension,
        timestampMs = timestampMs,
    )

    fun uniqueFile(
        directory: File,
        option: CompressionOption,
        timestampMs: Long = System.currentTimeMillis(),
    ): File {
        val base = fileName(option, timestampMs)
        var candidate = File(directory, base)
        var index = 2
        while (candidate.exists() && index < MAX_ATTEMPTS) {
            candidate = File(directory, OutputNaming.numbered(base, index))
            index++
        }
        return candidate
    }
}
