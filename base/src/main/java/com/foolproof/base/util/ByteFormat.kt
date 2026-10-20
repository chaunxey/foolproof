package com.foolproof.base.util

/**
 * 字节数的可读格式化。纯函数，供各 feature 的界面与日志复用。
 *
 * 从 `:features:image-compression` 下沉到 `:base`：重复照片清理也要展示「可释放 X MB」，
 * 而 feature 之间禁止互引（宪法 §3、§6）。
 */
object ByteFormat {

    fun format(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    /** 兼容旧调用点的命名。 */
    fun formatBytes(bytes: Long): String = format(bytes)
}
