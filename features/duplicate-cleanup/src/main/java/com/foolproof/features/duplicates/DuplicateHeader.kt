package com.foolproof.features.duplicates

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.foolproof.base.ads.AdGateProvider
import com.foolproof.base.ads.FeatureId
import com.foolproof.common.ui.FoolproofButton
import com.foolproof.base.ads.gateOrRun
import com.foolproof.base.util.ByteFormat
import com.foolproof.features.duplicates.ui.PhotoPreviewDialog
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/**
 * 重复照片清理的**头部**（标题、查询按钮、进度、勾选汇总、确认框与预览框）。
 *
 * 结果区的网格由 `:app` 的 `LazyVerticalGrid` 通过 `duplicatePhotoRows(...)` 贡献 item，
 * 这样缩略图是懒加载的（宪法 §3.2 v1.12）。
 */
@Composable
fun DuplicateHeader(
    modifier: Modifier = Modifier,
    viewModel: DuplicateViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()

    val permission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var message by remember { mutableStateOf<UiMessage?>(null) }
    var confirm by remember { mutableStateOf<ConfirmState?>(null) }
    var previewId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<List<String>>(emptyList()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        val permanentlyDenied = !granted &&
            activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        viewModel.dispatch(DuplicateAction.PermissionResolved(granted, permanentlyDenied))
    }

    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        viewModel.dispatch(DuplicateAction.SafTreeChosen(uri?.toString()))
    }

    val systemDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { _ ->
        // 系统回调码不代表每张都删成功 → 回到 ViewModel 用查询结果核实
        viewModel.dispatch(DuplicateAction.DeleteSettled(pendingDelete))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                DuplicateEffect.RequestPermission -> permissionLauncher.launch(permission)

                DuplicateEffect.OpenSettings -> context.openAppSettings()

                DuplicateEffect.RequestSafTree -> safLauncher.launch(null)

                is DuplicateEffect.ConfirmDelete -> confirm = ConfirmState(
                    count = effect.count,
                    bytes = effect.bytes,
                    irreversible = effect.irreversible,
                )

                is DuplicateEffect.LaunchSystemDelete -> {
                    val sender = (effect.token as? PendingIntent)?.intentSender
                    if (sender != null) {
                        pendingDelete = state.selectedIds.toList()
                        systemDeleteLauncher.launch(
                            IntentSenderRequest.Builder(sender).build(),
                        )
                    } else {
                        message = UiMessage(
                            textRes = R.string.duplicates_msg_system_delete_unavailable,
                            isError = true,
                        )
                    }
                }

                is DuplicateEffect.Message -> message = UiMessage(
                    textRes = effect.kind.labelRes(),
                    isError = effect.kind.isError(),
                )

                is DuplicateEffect.OpenPreview -> previewId = effect.id
            }
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            delay(2500)
            message = null
        }
    }

    // 每完成一次扫描记一次「成功使用」
    LaunchedEffect(state.scan) {
        if (state.scan is ScanState.Done) {
            AdGateProvider.gate?.onSuccess(FeatureId.DUPLICATE_CLEANUP)
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.duplicates_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            ScanButton(
                state = state,
                hasPermission = hasPermission,
                onRequestPermission = {
                    if (state.permission == PermissionState.DENIED_FOREVER) {
                        context.openAppSettings()
                    } else {
                        // 只申请权限；授权结果回调里再启动扫描（避免无权扫描白跑一次）
                        permissionLauncher.launch(permission)
                    }
                },
                onScan = {
                    AdGateProvider.gate.gateOrRun(activity, FeatureId.DUPLICATE_CLEANUP) {
                        viewModel.dispatch(DuplicateAction.RequestScan)
                    }
                },
                onCancel = { viewModel.dispatch(DuplicateAction.CancelScan) },
            )
        }

        state.scan.let { scan ->
            val text = when (scan) {
                is ScanState.Scanning -> stringResource(
                    R.string.duplicates_scanning,
                    scan.scanned,
                    scan.total,
                )
                is ScanState.Done -> stringResource(
                    R.string.duplicates_scan_done,
                    scan.scanned,
                    scan.skipped,
                )
                ScanState.Cancelled -> stringResource(R.string.duplicates_scan_cancelled)
                is ScanState.Failed -> stringResource(R.string.duplicates_scan_failed)
                ScanState.Idle -> null
            }
            text?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        message?.let { uiMessage ->
            Text(
                text = stringResource(uiMessage.textRes),
                color = if (uiMessage.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (state.groups.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.duplicates_selected_summary,
                        state.selectedCount,
                        ByteFormat.formatBytes(state.selectedBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { viewModel.dispatch(DuplicateAction.ClearSelection) }) {
                    Text(stringResource(R.string.duplicates_clear_selection))
                }
                Button(
                    onClick = { viewModel.dispatch(DuplicateAction.RequestDelete) },
                    enabled = state.canDelete,
                ) {
                    Text(stringResource(R.string.duplicates_delete))
                }
            }
            // 结果区由 :app 的 LazyVerticalGrid 以 item 形式贡献（duplicatePhotoRows），
            // 这里只留一行说明，避免把网格塞回 Section 里再次变成"非懒加载"。
            Text(
                text = stringResource(R.string.duplicates_results_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (state.scan !is ScanState.Done) {
                Icon(
                    painter = painterResource(R.drawable.ic_empty_duplicates),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp).size(40.dp),
                )
                Text(
                    text = stringResource(R.string.duplicates_empty_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val hint = when {
                state.scan is ScanState.Done -> stringResource(R.string.duplicates_hint_no_duplicates)
                state.scan is ScanState.Cancelled -> stringResource(R.string.duplicates_hint_cancelled)
                else -> stringResource(
                    R.string.duplicates_hint_idle,
                    stringResource(R.string.duplicates_scan_start),
                )
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }

    confirm?.let { current ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.duplicates_delete_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.duplicates_delete_message,
                            current.count,
                            ByteFormat.formatBytes(current.bytes),
                        ),
                    )
                    if (current.irreversible) {
                        Text(
                            stringResource(R.string.duplicates_delete_irreversible),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    viewModel.dispatch(DuplicateAction.ConfirmDelete)
                }) { Text(stringResource(R.string.duplicates_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.duplicates_cancel)) }
            },
        )
    }

    previewId?.let { id ->
        PhotoPreviewDialog(photoId = id, onDismiss = { previewId = null })
    }
}

@Composable
private fun ScanButton(
    state: DuplicateUiState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onScan: () -> Unit,
    onCancel: () -> Unit,
) {
    when {
        state.isScanning -> OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.duplicates_cancel))
        }
        state.permission == PermissionState.DENIED_FOREVER -> {
            OutlinedButton(onClick = onRequestPermission) {
                Text(stringResource(R.string.duplicates_open_settings))
            }
        }
        !hasPermission -> OutlinedButton(onClick = onRequestPermission) {
            Text(stringResource(R.string.duplicates_grant_and_scan))
        }
        state.hasResults || state.scan is ScanState.Done -> {
            FoolproofButton(
                text = stringResource(R.string.duplicates_scan_again),
                onClick = onScan,
            )
        }
        else -> FoolproofButton(
            text = stringResource(R.string.duplicates_scan_start),
            onClick = onScan,
        )
    }
}

/** 一次性提示 → 文案资源。 */
private fun DuplicateEffect.MessageKind.labelRes(): Int = when (this) {
    DuplicateEffect.MessageKind.EMPTY_GALLERY -> R.string.duplicates_msg_empty_gallery
    DuplicateEffect.MessageKind.NO_DUPLICATES -> R.string.duplicates_msg_no_duplicates
    DuplicateEffect.MessageKind.SCAN_FAILED -> R.string.duplicates_msg_scan_failed
    DuplicateEffect.MessageKind.PERMISSION_DENIED -> R.string.duplicates_msg_permission_denied
    DuplicateEffect.MessageKind.PERMISSION_DENIED_FOREVER ->
        R.string.duplicates_msg_permission_denied_forever
    DuplicateEffect.MessageKind.SAF_REQUIRED -> R.string.duplicates_msg_saf_required
    DuplicateEffect.MessageKind.DELETED -> R.string.duplicates_msg_deleted
    DuplicateEffect.MessageKind.DELETE_FAILED -> R.string.duplicates_msg_delete_failed
}

private fun DuplicateEffect.MessageKind.isError(): Boolean =
    this != DuplicateEffect.MessageKind.DELETED

/** 提示只存文案资源 id，渲染时再取当前语言文案。 */
private data class UiMessage(val textRes: Int, val isError: Boolean)

private data class ConfirmState(val count: Int, val bytes: Long, val irreversible: Boolean)

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

private fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}
