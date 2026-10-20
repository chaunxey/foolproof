package com.foolproof.features.duplicates.domain

/** 扫描过程中对外发出的事件（Flow 的一等公民，便于 UI 显示进度与取消）。 */
sealed interface ScanEvent {

    /** 开始扫描，[total] 为相册中的总张数（可能为 0）。 */
    data class Started(val total: Int) : ScanEvent

    data class Progress(val scanned: Int, val total: Int) : ScanEvent

    /** 成功算出一张图的哈希。 */
    data class Found(val item: PhotoItem) : ScanEvent

    /** 扫描结束；[skipped] 为无法读取/不支持的张数。 */
    data class Finished(val scanned: Int, val skipped: Int) : ScanEvent

    /** 扫描整体失败（权限、查询异常等），此时不会再有后续事件。 */
    data class Failed(val reason: ScanFailure) : ScanEvent
}

enum class ScanFailure {
    PERMISSION_DENIED,
    QUERY_FAILED,
    UNKNOWN,
}

/**
 * 相册扫描器。实现负责 MediaStore 查询、缩略图解码与哈希计算；
 * 抽成接口是为了让 ViewModel 可以在 JVM 单测里替换为假实现。
 */
interface PhotoScanner {
    fun scan(): kotlinx.coroutines.flow.Flow<ScanEvent>
}
