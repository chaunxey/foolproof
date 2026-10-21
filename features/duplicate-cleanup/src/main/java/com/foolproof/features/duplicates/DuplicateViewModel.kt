package com.foolproof.features.duplicates

import com.foolproof.base.log.LogUtil
import com.foolproof.base.mvi.BaseViewModel
import com.foolproof.base.mvi.UiAction
import com.foolproof.features.duplicates.domain.DeleteChannel
import com.foolproof.features.duplicates.domain.DeleteReport
import com.foolproof.features.duplicates.domain.DeleteResult
import com.foolproof.features.duplicates.domain.DuplicateGrouper
import com.foolproof.features.duplicates.domain.PhotoDeleter
import com.foolproof.features.duplicates.domain.PhotoItem
import com.foolproof.features.duplicates.domain.PhotoScanner
import com.foolproof.features.duplicates.domain.ScanEvent
import kotlinx.coroutines.Job

/**
 * 重复照片清理的 ViewModel（宪法 §4）。
 *
 * 扫描靠 [PhotoScanner] 的 Flow 驱动；分组与预选是纯函数；删除经 [PhotoDeleter] 分通道执行，
 * 且**以删除后的查询结果为准**（系统回调码不保证每张都删成功）。
 */
class DuplicateViewModel(
    private val scanner: PhotoScanner,
    private val deleter: PhotoDeleter,
    logger: LogUtil,
) : BaseViewModel<DuplicateUiState, DuplicateEffect>(
    DuplicateUiState(channel = deleter.channel()),
    logger,
) {

    private var scanJob: Job? = null

    override fun onAction(action: UiAction) {
        when (action) {
            DuplicateAction.RequestScan -> startScan()

            DuplicateAction.CancelScan -> cancelScan()

            is DuplicateAction.PermissionResolved -> onPermissionResolved(action)

            is DuplicateAction.Toggle -> toggle(action.id)

            is DuplicateAction.ToggleGroup -> toggleGroup(action.groupId)

            DuplicateAction.ClearSelection -> updateState { it.copy(selected = emptySet()) }

            is DuplicateAction.OpenPreview -> sendEffect(DuplicateEffect.OpenPreview(action.id))

            DuplicateAction.RequestDelete -> requestDelete()

            DuplicateAction.ConfirmDelete -> performDelete(currentState.selected)

            is DuplicateAction.DeleteSettled -> {
                if (action.systemConfirmed) {
                    settleSystemDelete(action.requested)
                } else {
                    settleDelete(action.requested)
                }
            }

            DuplicateAction.DeleteCancelled -> updateState { it.copy(deleting = false) }

            is DuplicateAction.SafTreeChosen -> {
                deleter.setSafTree(action.treeUri)
                updateState { it.copy(channel = deleter.channel()) }
            }

            DuplicateAction.DismissReport -> updateState { it.copy(report = null) }
        }
    }

    // region 权限

    private fun onPermissionResolved(action: DuplicateAction.PermissionResolved) {
        when {
            action.granted -> {
                updateState { it.copy(permission = PermissionState.GRANTED) }
                startScan()
            }

            action.permanentlyDenied -> {
                updateState { it.copy(permission = PermissionState.DENIED_FOREVER) }
                sendEffect(DuplicateEffect.Message(DuplicateEffect.MessageKind.PERMISSION_DENIED_FOREVER))
            }

            else -> {
                updateState { it.copy(permission = PermissionState.DENIED) }
                sendEffect(DuplicateEffect.Message(DuplicateEffect.MessageKind.PERMISSION_DENIED))
            }
        }
    }

    // endregion

    // region 扫描

    private fun startScan() {
        if (currentState.isScanning) return

        updateState {
            it.copy(
                scan = ScanState.Scanning(scanned = 0, total = 0),
                groups = emptyList(),
                selected = emptySet(),
                report = null,
            )
        }

        val found = mutableListOf<PhotoItem>()

        scanJob = launchJob {
            scanner.scan().collect { event ->
                when (event) {
                    is ScanEvent.Started -> updateState {
                        it.copy(scan = ScanState.Scanning(scanned = 0, total = event.total))
                    }

                    is ScanEvent.Progress -> updateState {
                        it.copy(scan = ScanState.Scanning(scanned = event.scanned, total = event.total))
                    }

                    is ScanEvent.Found -> found += event.item

                    is ScanEvent.Finished -> finishScan(found, event)

                    is ScanEvent.Failed -> {
                        updateState { it.copy(scan = ScanState.Failed(event.reason)) }
                        sendEffect(DuplicateEffect.Message(DuplicateEffect.MessageKind.SCAN_FAILED))
                    }
                }
            }
        }
    }

    private fun finishScan(found: List<PhotoItem>, event: ScanEvent.Finished) {
        val groups = DuplicateGrouper.group(found)
        updateState {
            it.copy(
                scan = ScanState.Done(scanned = event.scanned, skipped = event.skipped),
                groups = groups,
                // 宪法 v1.10：默认预选「除最大一张以外的其余项」，仍需用户确认才删
                selected = DuplicateGrouper.defaultSelection(groups),
            )
        }

        val message = when {
            event.scanned == 0 -> DuplicateEffect.MessageKind.EMPTY_GALLERY
            groups.isEmpty() -> DuplicateEffect.MessageKind.NO_DUPLICATES
            else -> null
        }
        message?.let { sendEffect(DuplicateEffect.Message(it)) }
    }

    private fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        updateState { it.copy(scan = ScanState.Cancelled) }
    }

    // endregion

    // region 勾选

    private fun toggle(id: String) {
        updateState { state ->
            val selected = if (id in state.selected) state.selected - id else state.selected + id
            state.copy(selected = selected)
        }
    }

    private fun toggleGroup(groupId: String) {
        val group = currentState.groups.firstOrNull { it.id == groupId } ?: return
        val deletable = DuplicateGrouper.defaultSelection(group)
        updateState { state ->
            val allSelected = state.selected.containsAll(deletable) && deletable.isNotEmpty()
            val selected = if (allSelected) state.selected - deletable else state.selected + deletable
            state.copy(selected = selected)
        }
    }

    // endregion

    // region 删除

    /** 点【删除】：先做通道与勾选校验，再让 UI 弹确认框。 */
    private fun requestDelete() {
        val state = currentState
        if (state.selected.isEmpty()) return

        if (state.channel == DeleteChannel.NONE) {
            sendEffect(DuplicateEffect.Message(DuplicateEffect.MessageKind.SAF_REQUIRED))
            sendEffect(DuplicateEffect.RequestSafTree)
            return
        }

        sendEffect(
            DuplicateEffect.ConfirmDelete(
                count = state.selectedCount,
                bytes = state.selectedBytes,
                irreversible = state.deleteIsIrreversible,
            ),
        )
    }

    private fun performDelete(ids: Set<String>) {
        if (ids.isEmpty()) return
        updateState { it.copy(deleting = true) }

        launch {
            when (val result = deleter.delete(ids.toList())) {
                is DeleteResult.Completed -> {
                    settleDelete(ids.toList())
                }

                is DeleteResult.NeedsConfirmation -> {
                    updateState { it.copy(deleting = false) }
                    sendEffect(DuplicateEffect.LaunchSystemDelete(result.token))
                }
            }
        }
    }

    private fun applyDeleted(removed: Set<String>, report: DeleteReport) {
        updateState { state ->
            val groups = state.groups.mapNotNull { group ->
                val remaining = group.items.filterNot { it.id in removed }
                // 只剩一张（或零张）的组没有清理价值，直接移除
                if (remaining.size > 1) group.copy(items = remaining) else null
            }
            state.copy(
                groups = groups,
                selected = state.selected - removed,
                deleting = false,
                report = report,
            )
        }
    }

    /**
     * 系统回收站已确认成功时直接按成功收尾。
     *
     * 注意：刚移入回收站的文件在短时间内仍可能被 openAssetFileDescriptor 打开，
     * 再用“文件还在不在”判断会把成功误报为部分失败。
     */
    private fun settleSystemDelete(requested: List<String>) {
        val report = DeleteReport(
            requested = requested.size,
            deleted = requested.size,
            failed = 0,
        )
        applyDeleted(requested.toSet(), report)
        sendEffect(
            DuplicateEffect.Message(DuplicateEffect.MessageKind.DELETED),
        )
    }

    /** 以「还在不在」为准收尾，而不是相信删除请求的返回码。 */
    private fun settleDelete(requested: List<String>) {
        launch {
            val existing = deleter.stillExisting(requested)
            val deletedCount = requested.size - existing.size
            val report = DeleteReport(
                requested = requested.size,
                deleted = deletedCount,
                failed = existing.size,
            )

            val removed = requested.filterNot { it in existing }.toSet()
            applyDeleted(removed, report)

            sendEffect(
                DuplicateEffect.Message(
                    if (report.allSucceeded) {
                        DuplicateEffect.MessageKind.DELETED
                    } else {
                        DuplicateEffect.MessageKind.DELETE_FAILED
                    },
                ),
            )
        }
    }

    // endregion
}
