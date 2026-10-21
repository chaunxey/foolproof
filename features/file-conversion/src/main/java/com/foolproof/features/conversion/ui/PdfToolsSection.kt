package com.foolproof.features.conversion.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.foolproof.base.util.ByteFormat
import com.foolproof.common.ui.FoolproofButton
import com.foolproof.common.ui.FoolproofSecondaryButton
import com.foolproof.features.conversion.PdfPick
import com.foolproof.features.conversion.PdfToolPhase
import com.foolproof.features.conversion.PdfToolsAction
import com.foolproof.features.conversion.PdfToolsEffect
import com.foolproof.features.conversion.PdfToolsViewModel
import com.foolproof.features.conversion.R
import com.foolproof.features.conversion.domain.PdfToolMode
import java.io.File
import org.koin.compose.viewmodel.koinViewModel

/** 离线 PDF 工具：合并 / 按范围拆分 / 旋转。 */
@Composable
fun PdfToolsSection(
    modifier: Modifier = Modifier,
    viewModel: PdfToolsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var message by remember { mutableStateOf<Int?>(null) }
    val working = state.phase == PdfToolPhase.WORKING

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.dispatch(
                PdfToolsAction.PdfsPicked(
                    uris.map { PdfPick(it.toString(), context.pdfDisplayName(it)) },
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PdfToolsEffect.Message -> message = effect.kind.labelRes()
                is PdfToolsEffect.Share -> {
                    if (!context.sharePdfFile(effect.path)) message = R.string.pdf_tools_failed
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PdfToolMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { viewModel.dispatch(PdfToolsAction.ModeChanged(mode)) },
                    enabled = !working,
                    label = { Text(stringResource(mode.labelRes())) },
                )
            }
        }

        FoolproofSecondaryButton(
            text = stringResource(R.string.pdf_tools_pick),
            onClick = { picker.launch(arrayOf("application/pdf")) },
            enabled = !working,
            modifier = Modifier.padding(top = 12.dp),
        )

        state.files.forEachIndexed { index, file ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.pdf_tools_pages, file.pages),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (index > 0) {
                    TextButton(
                        onClick = { viewModel.dispatch(PdfToolsAction.MoveUp(file.uri)) },
                        enabled = !working,
                    ) { Text("↑") }
                }
                TextButton(
                    onClick = { viewModel.dispatch(PdfToolsAction.Remove(file.uri)) },
                    enabled = !working,
                ) { Text(stringResource(R.string.pdf_tools_remove)) }
            }
        }

        when (state.mode) {
            PdfToolMode.SPLIT -> {
                OutlinedTextField(
                    value = state.range,
                    onValueChange = { viewModel.dispatch(PdfToolsAction.RangeChanged(it)) },
                    label = { Text(stringResource(R.string.pdf_tools_range)) },
                    singleLine = true,
                    enabled = !working,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            PdfToolMode.ROTATE -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    listOf(90, 180, 270).forEach { degrees ->
                        FilterChip(
                            selected = state.rotateDegrees == degrees,
                            onClick = {
                                viewModel.dispatch(PdfToolsAction.RotateChanged(degrees))
                            },
                            enabled = !working,
                            label = { Text("$degrees°") },
                        )
                    }
                }
            }

            PdfToolMode.MERGE -> Unit
        }

        FoolproofButton(
            text = stringResource(R.string.pdf_tools_start),
            onClick = { viewModel.dispatch(PdfToolsAction.Start) },
            enabled = state.canStart,
            loading = working,
            modifier = Modifier.padding(top = 12.dp),
        )

        message?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        state.output?.let { output ->
            Text(
                text = stringResource(R.string.pdf_tools_done) +
                    " · " + ByteFormat.formatBytes(output.bytes),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            PdfPreview(path = output.path, modifier = Modifier.padding(top = 8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FoolproofSecondaryButton(
                    text = stringResource(R.string.conversion_share),
                    onClick = { viewModel.dispatch(PdfToolsAction.Share) },
                )
                if (state.canSaveToDownloads) {
                    FoolproofSecondaryButton(
                        text = stringResource(R.string.conversion_save_to_downloads),
                        onClick = { viewModel.dispatch(PdfToolsAction.SaveToDownloads) },
                    )
                }
            }
        }
    }
}

private fun PdfToolMode.labelRes(): Int = when (this) {
    PdfToolMode.MERGE -> R.string.pdf_tools_mode_merge
    PdfToolMode.SPLIT -> R.string.pdf_tools_mode_split
    PdfToolMode.ROTATE -> R.string.pdf_tools_mode_rotate
}

private fun PdfToolsEffect.MessageKind.labelRes(): Int = when (this) {
    PdfToolsEffect.MessageKind.NEED_ONE -> R.string.pdf_tools_need_one
    PdfToolsEffect.MessageKind.NEED_TWO -> R.string.pdf_tools_need_two
    PdfToolsEffect.MessageKind.INVALID_RANGE -> R.string.pdf_tools_invalid_range
    PdfToolsEffect.MessageKind.FAILED -> R.string.pdf_tools_failed
    PdfToolsEffect.MessageKind.DONE -> R.string.pdf_tools_done
    PdfToolsEffect.MessageKind.SAVED -> R.string.conversion_msg_saved
    PdfToolsEffect.MessageKind.SAVE_FAILED -> R.string.conversion_msg_save_failed
}

private fun Context.pdfDisplayName(uri: Uri): String = runCatching {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "document.pdf"

private fun Context.sharePdfFile(path: String): Boolean {
    val uri = runCatching {
        FileProvider.getUriForFile(this, "$packageName.files", File(path))
    }.getOrNull() ?: return false
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        startActivity(
            Intent.createChooser(intent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
        true
    }.getOrDefault(false)
}
