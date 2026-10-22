package com.foolproof.features.conversion.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.produceState
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
import com.foolproof.common.ui.SuccessDialog
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
                    payload.uris.map { uri ->
                        val name = context.pdfDisplayName(Uri.parse(uri))
                        // 分享进来的 Uri 权限/Provider 不一定稳定，先落到自有缓存再解析
                        val local = context.cacheSharedPdf(uri)
                        PdfPick(local ?: uri, name)
                    },
                ),
            )
            com.foolproof.base.share.SharedContentStore.consume()
        }
    }
    var message by remember { mutableStateOf<Int?>(null) }
    var messageError by remember { mutableStateOf(false) }
    val working = state.phase == PdfToolPhase.WORKING
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(state.phase) {
        if (state.phase == PdfToolPhase.DONE && state.mode != PdfToolMode.IMAGES) {
            showSuccess = true
        }
    }

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

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 16.dp)) {
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

            PdfToolMode.COMPRESS,
            PdfToolMode.IMAGES,
            -> {
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

        if (state.mode == PdfToolMode.IMAGES && state.images.isNotEmpty()) {
            Text(
                text = stringResource(R.string.pdf_tools_images_done, state.images.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                state.images.forEach { image ->
                    ImageThumb(path = image.path)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                FoolproofSecondaryButton(
                    text = stringResource(R.string.pdf_tools_share_images),
                    onClick = {
                        if (!context.shareImages(state.images.map { it.path })) {
                            message = R.string.pdf_tools_share_failed
                            messageError = true
                        }
                    },
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    FoolproofSecondaryButton(
                        text = stringResource(R.string.conversion_save_to_downloads),
                        onClick = {
                            val ok = context.saveImagesToDownloads(state.images.map { it.path })
                            message = if (ok) {
                                R.string.conversion_msg_saved
                            } else {
                                R.string.conversion_msg_save_failed
                            }
                            messageError = !ok
                        },
                    )
                }
            }
        }

        if (state.mode != PdfToolMode.IMAGES) {
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
        if (showSuccess) {
            state.output?.let { output ->
                SuccessDialog(
                    title = stringResource(R.string.pdf_tools_done),
                    summary = ByteFormat.formatBytes(output.bytes),
                    onDismiss = { showSuccess = false },
                    preview = {
                        PdfPreview(path = output.path, modifier = Modifier.fillMaxWidth())
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                if (!context.openPdfFile(output.path)) {
                                    message = R.string.conversion_msg_no_pdf_app
                                    messageError = true
                                }
                            },
                        ) {
                            Text(stringResource(R.string.conversion_open))
                        }
                        TextButton(
                            onClick = { viewModel.dispatch(PdfToolsAction.Share) },
                        ) {
                            Text(stringResource(R.string.conversion_share))
                        }
                        if (state.canSaveToDownloads) {
                            TextButton(
                                onClick = { viewModel.dispatch(PdfToolsAction.SaveToDownloads) },
                            ) {
                                Text(stringResource(R.string.conversion_save_to_downloads))
                            }
                        }
                    },
                )
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
    PdfToolMode.IMAGES -> R.string.pdf_tools_mode_images
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
    PdfToolsEffect.MessageKind.LOAD_FAILED -> R.string.pdf_tools_load_failed
    PdfToolsEffect.MessageKind.FAILED -> R.string.pdf_tools_failed
    PdfToolsEffect.MessageKind.DONE -> R.string.pdf_tools_done
    PdfToolsEffect.MessageKind.SAVED -> R.string.conversion_msg_saved
    PdfToolsEffect.MessageKind.SAVE_FAILED -> R.string.conversion_msg_save_failed
}

private fun PdfToolsEffect.MessageKind.isError(): Boolean = when (this) {
    PdfToolsEffect.MessageKind.NEED_ONE,
    PdfToolsEffect.MessageKind.NEED_TWO,
    PdfToolsEffect.MessageKind.NEED_PAGES,
    PdfToolsEffect.MessageKind.LOAD_FAILED,
    PdfToolsEffect.MessageKind.INVALID_RANGE,
    PdfToolsEffect.MessageKind.FAILED,
    PdfToolsEffect.MessageKind.SAVE_FAILED,
    -> true

    PdfToolsEffect.MessageKind.DONE,
    PdfToolsEffect.MessageKind.SAVED,
    -> false
}

private fun Context.shareImages(paths: List<String>): Boolean {
    if (paths.isEmpty()) return false
    return runCatching {
        val uris = paths.map {
            FileProvider.getUriForFile(this, "$packageName.files", File(it))
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/jpeg"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(
            Intent.createChooser(intent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }.isSuccess
}

/** 保存到「下载/Foolproof」；仅 API 29+ 可用（与 PDF 保存一致）。 */
private fun Context.saveImagesToDownloads(paths: List<String>): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    var saved = false
    paths.forEach { path ->
        val file = File(path)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/Foolproof")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching {
            contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return@forEach
        val ok = runCatching {
            contentResolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: error("no output stream")
        }.isSuccess
        if (ok) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
            saved = true
        } else {
            contentResolver.delete(uri, null, null)
        }
    }
    return saved
}

@Composable
private fun ImageThumb(path: String) {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            runCatching { BitmapFactory.decodeFile(path, options) }.getOrNull()
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
        )
    }
}

private fun Context.cacheSharedPdf(uri: String): String? = runCatching {
    val parsed = Uri.parse(uri)
    if (parsed.scheme == null) return uri
    if (parsed.scheme == "file") return parsed.path
    val dir = File(cacheDir, "shared_pdf").apply { mkdirs() }
    dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < System.currentTimeMillis() - 3600_000L) it.delete() }
    val target = File(dir, "shared_${System.currentTimeMillis()}.pdf")
    contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
        target.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    target.absolutePath
}.getOrNull()

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
