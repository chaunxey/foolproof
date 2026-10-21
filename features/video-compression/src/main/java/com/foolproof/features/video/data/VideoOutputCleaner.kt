package com.foolproof.features.video.data

import com.foolproof.base.log.LogUtil
import java.io.File

/**
 * 启动时清理视频输出目录。
 *
 * 为什么需要它：取消/失败路径都会删半成品，但**进程被杀**时那些代码根本来不及执行，
 * 于是 `files/videos/` 会留下不完整的 `.mp4`。FR-014 要求「不得留下孤立文件」，
 * 因此应用启动时扫一遍：此时目录里的一切都来自上一个进程，可以安全删除
 * （本次进程还没开始写任何东西）。
 *
 * 与之配套的是 ViewModel 里的 `cleanupOrphanOutputs`：处理"同一进程内、上一个 VM 实例"
 * 留下的残留；两者互补。
 */
class VideoOutputCleaner(
    private val outputDir: File,
    private val log: LogUtil,
) {

    /** @return 删除的文件数（用于日志与测试断言）。 */
    fun cleanAll(): Int {
        if (!outputDir.exists()) return 0
        var deleted = 0
        runCatching {
            outputDir.listFiles()?.forEach { file ->
                if (file.isFile && file.delete()) {
                    deleted++
                    log.d { "startup cleanup: removed ${file.name}" }
                }
            }
        }.onFailure { log.w { "startup cleanup failed: ${it::class.simpleName}" } }
        return deleted
    }
}
