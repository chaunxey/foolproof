package com.foolproof.features.duplicates

import com.foolproof.base.mvi.UiAction
import com.foolproof.base.mvi.UiEffect
import com.foolproof.base.mvi.UiState
import com.foolproof.features.duplicates.domain.DeleteChannel
import com.foolproof.features.duplicates.domain.DeleteReport
import com.foolproof.features.duplicates.domain.PhotoGroup
import com.foolproof.features.duplicates.domain.ScanFailure

/** 扫描状态机。 */
sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val scanned: Int, val total: Int) : ScanState
    data class Done(val scanned: Int, val skipped: Int) : ScanState
    data object Cancelled : ScanState
    data class Failed(val reason: ScanFailure) : ScanState
}

/** 相册读取权限的粗粒度状态，用于决定按钮文案与提示。 */
enum class PermissionState {
    UNKNOWN,
    GRANTED,
    DENIED,
    DENIED_FOREVER,
}

data class DuplicateUiState(
    val scan: ScanState = ScanState.Idle,
    val groups: List<PhotoGroup> = emptyList(),
    val selected: Set<String> = emptySet(),
    val permission: PermissionState = PermissionState.UNKNOWN,
    val deleting: Boolean = false,
    val channel: DeleteChannel = DeleteChannel.NONE,
    val report: DeleteReport? = null,
) : UiState {

    val isScanning: Boolean
        get() = scan is ScanState.Scanning

    val hasResults: Boolean
        get() = groups.isNotEmpty()

    val selectedIds: Set<String>
        get() = selected

    val selectedCount: Int
        get() = selected.size

    val selectedBytes: Long
        get() = groups.flatMap { it.items }
            .filter { it.id in selected }
            .sumOf { it.sizeBytes }

    /** 低版本走 SAF，删除不可恢复；用于确认框文案与按钮态。 */
    val deleteIsIrreversible: Boolean
        get() = channel == DeleteChannel.SAF

    val canDelete: Boolean
        get() = selected.isNotEmpty() && !deleting && channel != DeleteChannel.NONE
}

sealed interface DuplicateAction : UiAction {

    data object RequestScan : DuplicateAction

    data object CancelScan : DuplicateAction

    data class PermissionResolved(val granted: Boolean, val permanentlyDenied: Boolean) : DuplicateAction

    data class Toggle(val id: String) : DuplicateAction

    data class ToggleGroup(val groupId: String) : DuplicateAction

    data object ClearSelection : DuplicateAction

    data class OpenPreview(val id: String) : DuplicateAction

    /** 点【删除】：先校验通道与勾选，再请求 UI 弹确认框。 */
    data object RequestDelete : DuplicateAction

    /** 用户在确认框点了确认。 */
    data object ConfirmDelete : DuplicateAction

    /** 系统删除对话框返回后，用实际查询结果收尾。 */
    data class DeleteSettled(val requested: List<String>) : DuplicateAction

    data class SafTreeChosen(val treeUri: String?) : DuplicateAction

    data object DismissReport : DuplicateAction
}

sealed interface DuplicateEffect : UiEffect {

    data object RequestPermission : DuplicateEffect

    data object OpenSettings : DuplicateEffect

    /** 低版本且未授权 SAF 目录：请求 UI 拉起目录选择。 */
    data object RequestSafTree : DuplicateEffect

    /** 请求 UI 弹出二次确认框（宪法 §3.2：删除前必须有确认）。 */
    data class ConfirmDelete(
        val count: Int,
        val bytes: Long,
        val irreversible: Boolean,
    ) : DuplicateEffect

    /** 请求 UI 拉起系统删除确认（token 为 PendingIntent）。 */
    data class LaunchSystemDelete(val token: Any) : DuplicateEffect

    data class Message(val kind: MessageKind) : DuplicateEffect

    data class OpenPreview(val id: String) : DuplicateEffect

    enum class MessageKind {
        EMPTY_GALLERY,
        NO_DUPLICATES,
        SCAN_FAILED,
        PERMISSION_DENIED,
        PERMISSION_DENIED_FOREVER,

        /** 低版本未授权目录，无法删除。 */
        SAF_REQUIRED,

        /** 删除完成（可能含失败项，具体看报告）。 */
        DELETED,
        DELETE_FAILED,
    }
}
