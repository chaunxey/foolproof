package com.foolproof.base.log

import com.foolproof.base.BuildConfig

/**
 * 日志工具（宪法 §6）。由 Koin 以单例提供（宪法 §5），业务侧注入使用，不再依赖全局 object。
 *
 * - 仅 debug 构建输出，release 完全静默；
 * - 输出带「调用处文件名:行号」；
 * - 敏感串（API Key、Authorization 头）自动脱敏；
 * - 参数是 lambda：关闭时**不会求值**，因此没有字符串拼接开销。
 *
 * 生产代码禁止直接使用 `android.util.Log`，唯一例外是本文件的默认 [sink]。
 */
class LogUtil(enabled: Boolean = BuildConfig.DEBUG) {

    private val apiKey = Regex("sk-[A-Za-z0-9_\\-]{6,}")
    private val authHeader = Regex("(?i)(authorization\\s*[:=]\\s*)(\\S+)")

    /** 输出实现，测试时可替换。 */
    var sink: (String) -> Unit = { line -> android.util.Log.d(TAG, line) }

    /** 总开关。release 构建下无法被打开（初始值同样受此约束）。 */
    var enabled: Boolean = enabled && BuildConfig.DEBUG
        set(value) {
            field = value && BuildConfig.DEBUG
        }

    fun d(message: () -> String) = write("D", message)

    fun i(message: () -> String) = write("I", message)

    fun w(message: () -> String) = write("W", message)

    fun e(message: () -> String) = write("E", message)

    /** 对任意文本做脱敏，便于在其它日志通道复用。 */
    fun sanitize(text: String): String = authHeader
        .replace(apiKey.replace(text) { key -> "sk-***${key.value.takeLast(4)}" }) { match ->
            "${match.groupValues[1]}***"
        }

    private fun write(level: String, message: () -> String) {
        if (!enabled) return
        sink("${callerTag()} $level ${sanitize(message())}")
    }

    /** 取第一个不属于 LogUtil 的调用栈帧，得到「文件名:行号」。 */
    private fun callerTag(): String {
        val self = LogUtil::class.java.name
        val frame = Throwable().stackTrace.firstOrNull { frame ->
            frame.className != self && !frame.className.startsWith("java.lang.")
        } ?: return "Unknown:0"
        return "${frame.fileName ?: "Unknown"}:${frame.lineNumber}"
    }

    companion object {
        private const val TAG = "Foolproof"
    }
}
