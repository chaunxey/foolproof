package com.foolproof.features.conversion.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.foolproof.features.conversion.domain.PdfCompressQuality
import com.foolproof.features.conversion.domain.PdfToolMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/** 离线 PDF 工具：合并 / 按范围拆分 / 旋转 / 页面重排 / 压缩。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfToolsSection(
    modifier: Modifier = Modifier,
    viewModel: PdfToolsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val shared by com.foolproof.base.share.SharedContentStore.content.collectAsState()
    LaunchedEffect(shared) {
        val payload = shared
        if (payload?.kind == com.foolproof.base.share.SharedContentStore.Kind.PDF) {
            viewModel.dispatch(
                PdfToolsAction.PdfsPicked(
                    payload.uris.map {
                        PdfPick(it, context.pdfDisplayName(Uri.parse(it)))
                    },
                ),
            )
            com.foolproof.base.share.SharedContentStore.consume()
        }
    }
    var message by remember { mutableStateOf<Int?>(null) }
    var messageError by remember { mutableStateOf(false) }
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
                is PdfToolsEffect.Message -> {
                    message = effect.kind.labelRes()
                    messageError = effect.kind.isError()
                }
                is PdfToolsEffect.Share -> {
                    if (!context.sharePdfFile(effect.path)) message = R.string.pdf_tools_failed
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

            PdfToolMode.PAGES -> {
                val file = state.files.firstOrNull()
                if (file != null) {
                    state.pageOrder.forEachIndexed { index, pageNumber ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PdfPageThumb(
                                uri = file.uri,
                                pageIndex = pageNumber - 1,
                                modifier = Modifier.size(56.dp),
                            )
                            Text(
                                text = stringResource(R.string.pdf_tools_pages, pageNumber),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).padding(start = 12.dp),
                            )
                            TextButton(
                                onClick = { viewModel.dispatch(PdfToolsAction.PageMove(index, -1)) },
                                enabled = !working && index > 0,
                            ) { Text("←") }
                            TextButton(
                                onClick = { viewModel.dispatch(PdfToolsAction.PageMove(index, 1)) },
                                enabled = !working && index < state.pageOrder.lastIndex,
                            ) { Text("→") }
                            TextButton(
                                onClick = { viewModel.dispatch(PdfToolsAction.PageRemove(index)) },
                                enabled = !working,
                            ) { Text(stringResource(R.string.pdf_tools_remove)) }
                        }
                    }
                }
            }

            PdfToolMode.COMPRESS -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    com.foolproof.features.conversion.domain.PdfCompressQuality.entries
                        .forEach { quality ->
                            FilterChip(
                                selected = state.quality == quality,
                                onClick = {
                                    viewModel.dispatch(PdfToolsAction.QualityChanged(quality))
                                },
                                enabled = !working,
                                label = { Text(stringResource(quality.labelRes())) },
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

        message?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodySmall,
                color = if (messageError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
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
                    text = stringResource(R.string.conversion_open),
                    onClick = {
                        if (!context.openPdfFile(output.path)) {
                            message = R.string.conversion_msg_no_pdf_app
                            messageError = true
                        }
                    },
                )
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
    PdfToolMode.PAGES -> R.string.pdf_tools_mode_pages
    PdfToolMode.COMPRESS -> R.string.pdf_tools_mode_compress
}

private fun PdfCompressQuality.labelRes(): Int = when (this) {
    PdfCompressQuality.HIGH -> R.string.pdf_tools_quality_high
    PdfCompressQuality.MEDIUM -> R.string.pdf_tools_quality_medium
    PdfCompressQuality.LOW -> R.string.pdf_tools_quality_low
}

private fun PdfToolsEffect.MessageKind.labelRes(): Int = when (this) {
    PdfToolsEffect.MessageKind.NEED_ONE -> R.string.pdf_tools_need_one
    PdfToolsEffect.MessageKind.NEED_TWO -> R.string.pdf_tools_need_two
    PdfToolsEffect.MessageKind.NEED_PAGES -> R.string.pdf_tools_need_pages
    PdfToolsEffect.MessageKind.INVALID_RANGE -> R.string.pdf_tools_invalid_range
    PdfToolsEffect.MessageKind.FAILED -> R.string.pdf_tools_failed
    PdfToolsEffect.MessageKind.DONE -> R.string.pdf_tools_done
    PdfToolsEffect.MessageKind.SAVED -> R.string.conversion_msg_saved
    PdfToolsEffect.MessageKind.SAVE_FAILED -> R.string.conversion_msg_save_failed
}

private fun PdfToolsEffect.MessageKind.isError(): Boolean = when (this) {
    PdfToolsEffect.MessageKind.NEED_ONE,
    PdfToolsEffect.MessageKind.NEED_TWO,
    PdfToolsEffect.MessageKind.NEED_PAGES,
    PdfToolsEffect.MessageKind.INVALID_RANGE,
    PdfToolsEffect.MessageKind.FAILED,
    PdfToolsEffect.MessageKind.SAVE_FAILED,
    -> true

    PdfToolsEffect.MessageKind.DONE,
    PdfToolsEffect.MessageKind.SAVED,
    -> false
}

private fun Context.pdfDisplayName(uri: Uri): String {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    val fromProvider = runCatching {
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && cursor.columnCount > 0) cursor.getString(0) else null
        }
    }.getOrNull()
    if (!fromProvider.isNullOrBlank()) return fromProvider

    val documentId = runCatching {
        android.provider.DocumentsContract.getDocumentId(uri)
    }.getOrNull()
    if (!documentId.isNullOrBlank()) {
        val name = documentId.substringAfter(':', documentId).substringAfterLast('/')
        if (name.isNotBlank()) return name
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
}

@Composable
private fun PdfPageThumb(uri: String, pageIndex: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, pageIndex) {
        bitmap = withContext(Dispatchers.IO) {
            renderPdfPage(context, uri, pageIndex)
        }
    }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

private fun renderPdfPage(context: Context, uri: String, index: Int): Bitmap? = runCatching {
    val descriptor = context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
        ?: return@runCatching null
    try {
        val renderer = PdfRenderer(descriptor)
        try {
            if (index !in 0 until renderer.pageCount) return@runCatching null
            val page = renderer.openPage(index)
            try {
                val width = 160
                val height = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            } finally {
                page.close()
            }
        } finally {
            renderer.close()
        }
    } finally {
        descriptor.close()
    }
}.getOrNull()

private fun Context.openPdfFile(path: String): Boolean {
    val uri = runCatching {
        FileProvider.getUriForFile(this, "$packageName.files", File(path))
    }.getOrNull() ?: return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}

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
