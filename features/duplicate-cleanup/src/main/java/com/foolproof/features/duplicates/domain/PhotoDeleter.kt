package com.foolproof.features.duplicates.domain

/**
 * 删除能力的抽象。两套通道由实现决定，ViewModel 只关心「能不能删」与「删完的报告」。
 *
 * [DeleteResult.NeedsConfirmation] 里携带一个**不透明 token**（Android 实现放 `PendingIntent`），
 * 目的是让 ViewModel 在 JVM 单测里可用，不必构造平台对象。UI 侧把它转成实际的系统请求。
 */
interface PhotoDeleter {

    /** 当前可用的删除通道。 */
    fun channel(): DeleteChannel

    /** API ≤ 29 使用 SAF：由 UI 在用户授权目录后写入。 */
    fun setSafTree(treeUri: String?)

    /**
     * 执行删除。
     *
     * - `SYSTEM_REQUEST` 通道返回 [DeleteResult.NeedsConfirmation]，等 UI 拉起系统确认后再调 [verify]；
     * - `SAF` 通道直接删除并返回 [DeleteResult.Completed]；
     * - `NONE` 通道直接返回全部失败。
     */
    suspend fun delete(ids: List<String>): DeleteResult

    /**
     * 删除后核实真实结果：返回**仍然存在**的 id 集合。
     * 系统删除请求的回调码并不保证每张都删成功，必须以查询结果为准。
     */
    suspend fun stillExisting(ids: List<String>): Set<String>
}

enum class DeleteChannel {
    /** API ≥ 30：系统回收站删除请求（可恢复）。 */
    SYSTEM_REQUEST,

    /** API ≤ 29：SAF 目录删除（不可恢复）。 */
    SAF,

    /** 当前不可删除（低版本且未授权目录）。 */
    NONE,
}

sealed interface DeleteResult {
    data class Completed(val report: DeleteReport) : DeleteResult

    /** 需要 UI 拉起系统确认；[token] 为平台对象，由实现解释。 */
    data class NeedsConfirmation(val token: Any, val requested: List<String>) : DeleteResult
}

data class DeleteReport(
    val requested: Int,
    val deleted: Int,
    val failed: Int,
) {
    val allSucceeded: Boolean
        get() = failed == 0
}
