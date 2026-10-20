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
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
                        message = UiMessage("系统删除请求不可用", isError = true)
                    }
                }

                is DuplicateEffect.Message -> message = UiMessage(
                    text = effect.kind.label(),
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

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("重复照片清理", style = MaterialTheme.typography.titleMedium)
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
                onScan = { viewModel.dispatch(DuplicateAction.RequestScan) },
                onCancel = { viewModel.dispatch(DuplicateAction.CancelScan) },
            )
        }

        state.scan.let { scan ->
            val text = when (scan) {
                is ScanState.Scanning -> "扫描中 ${scan.scanned}/${scan.total}"
                is ScanState.Done -> "已扫描 ${scan.scanned} 张，跳过 ${scan.skipped} 张"
                ScanState.Cancelled -> "已取消"
                is ScanState.Failed -> "扫描失败"
                ScanState.Idle -> null
            }
            text?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        message?.let {
            Text(
                text = it.text,
                color = if (it.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
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
                    text = "已选 ${state.selectedCount} 张 · ${ByteFormat.formatBytes(state.selectedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { viewModel.dispatch(DuplicateAction.ClearSelection) }) {
                    Text("清空选择")
                }
                Button(
                    onClick = { viewModel.dispatch(DuplicateAction.RequestDelete) },
                    enabled = state.canDelete,
                ) {
                    Text("删除")
                }
            }
            // 结果区由 :app 的 LazyVerticalGrid 以 item 形式贡献（duplicatePhotoRows），
            // 这里只留一行说明，避免把网格塞回 Section 里再次变成"非懒加载"。
            Text(
                text = "结果见下方，点击缩略图可放大查看",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val hint = when {
                state.scan is ScanState.Done -> "没有发现重复照片"
                state.scan is ScanState.Cancelled -> "已取消，可重新查询"
                else -> "点击「查询相同图片」扫描相册中的重复照片"
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
            title = { Text("删除确认") },
            text = {
                Column {
                    Text("将删除 ${current.count} 张，约 ${ByteFormat.formatBytes(current.bytes)}")
                    if (current.irreversible) {
                        Text(
                            "当前系统版本删除后无法恢复",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    viewModel.dispatch(DuplicateAction.ConfirmDelete)
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("取消") }
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
        state.isScanning -> OutlinedButton(onClick = onCancel) { Text("取消") }
        state.permission == PermissionState.DENIED_FOREVER -> {
            OutlinedButton(onClick = onRequestPermission) { Text("去设置授权") }
        }
        !hasPermission -> OutlinedButton(onClick = onRequestPermission) { Text("授权并查询") }
        state.hasResults || state.scan is ScanState.Done -> {
            Button(onClick = onScan) { Text("重新查询") }
        }
        else -> Button(onClick = onScan) { Text("查询相同图片") }
    }
}

private fun DuplicateEffect.MessageKind.label(): String = when (this) {
    DuplicateEffect.MessageKind.EMPTY_GALLERY -> "相册里没有可读取的图片"
    DuplicateEffect.MessageKind.NO_DUPLICATES -> "没有发现重复照片"
    DuplicateEffect.MessageKind.SCAN_FAILED -> "扫描失败，请重试"
    DuplicateEffect.MessageKind.PERMISSION_DENIED -> "需要相册读取权限才能扫描"
    DuplicateEffect.MessageKind.PERMISSION_DENIED_FOREVER -> "权限已被永久拒绝，请到系统设置中开启"
    DuplicateEffect.MessageKind.SAF_REQUIRED -> "当前系统需先授权一个目录才能删除"
    DuplicateEffect.MessageKind.DELETED -> "已删除"
    DuplicateEffect.MessageKind.DELETE_FAILED -> "部分图片删除失败"
}

private fun DuplicateEffect.MessageKind.isError(): Boolean =
    this != DuplicateEffect.MessageKind.DELETED

private data class UiMessage(val text: String, val isError: Boolean)

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
