package com.foolproof.features.conversion.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.Intent
import java.io.File
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp

import com.foolproof.base.ads.AdGateProvider
import com.foolproof.base.ads.FeatureId
import com.foolproof.common.ui.FoolproofButton
import com.foolproof.common.ui.FoolproofSecondaryButton
import com.foolproof.base.ads.gateOrRun
import com.foolproof.base.util.ByteFormat
import com.foolproof.base.util.findActivity
import com.foolproof.features.conversion.ConversionAction
import com.foolproof.features.conversion.ConversionEffect
import com.foolproof.features.conversion.ConversionMode
import com.foolproof.features.conversion.ConversionPhase
import com.foolproof.features.conversion.FileConversionViewModel
import com.foolproof.features.conversion.R
import com.foolproof.features.conversion.domain.PdfPageSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/**
 * 文件转 PDF 的对外入口（宪法 §3：只提供 Section，页面由 `:app` 组装为独立 tab）。
 *
 * 本组件不自带滚动容器，滚动由 `:app` 提供。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileConversionSection(
    modifier: Modifier = Modifier,
    viewModel: FileConversionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context.findActivity()
    var message by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            message = R.string.conversion_camera_denied to true
        }
    }

    // 每成功生成一次 PDF 记一次
    LaunchedEffect(state.phase) {
        if (state.phase is ConversionPhase.Done) {
            AdGateProvider.gate?.onSuccess(FeatureId.FILE_CONVERSION)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.dispatch(ConversionAction.ImagesPicked(uris.map { it.toString() }))
        }
    }

    val textPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val meta = context.readFileMeta(uri)
            viewModel.dispatch(
                ConversionAction.TextPicked(
                    uri = uri.toString(),
                    displayName = meta.first,
                    bytes = meta.second,
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ConversionEffect.Message -> message = effect.kind.labelRes() to effect.kind.isError()
                is ConversionEffect.Share ->
                    if (!context.sharePdf(effect.path)) message = R.string.conversion_msg_no_share_app to true
                is ConversionEffect.Open ->
                    if (!context.openPdf(effect.path)) message = R.string.conversion_msg_no_pdf_app to true
            }
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(3000)
            message = null
        }
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.conversion_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // 没有输入/结果时重置没有意义，隐藏按钮
            if (
                state.images.isNotEmpty() ||
                state.text != null ||
                state.phase != ConversionPhase.Idle
            ) {
                TextButton(onClick = { viewModel.dispatch(ConversionAction.Reset) }) {
                    Text(stringResource(R.string.conversion_reset))
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FilterChip(
                selected = state.mode == ConversionMode.IMAGES,
                onClick = { viewModel.dispatch(ConversionAction.ModeChanged(ConversionMode.IMAGES)) },
                enabled = !state.isConverting,
                label = { Text(stringResource(R.string.conversion_mode_images)) },
            )
            FilterChip(
                selected = state.mode == ConversionMode.TEXT,
                onClick = { viewModel.dispatch(ConversionAction.ModeChanged(ConversionMode.TEXT)) },
                enabled = !state.isConverting,
                label = { Text(stringResource(R.string.conversion_mode_text)) },
            )
        }

        when (state.mode) {
            ConversionMode.IMAGES -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    FoolproofSecondaryButton(
                        text = if (state.images.isEmpty()) {
                            stringResource(R.string.conversion_pick_images)
                        } else {
                            stringResource(R.string.conversion_add_images)
                        },
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        enabled = !state.isConverting,
                    )
                    if (hasCamera) {
                        FoolproofSecondaryButton(
                            text = stringResource(R.string.conversion_take_photo),
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA,
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) showCamera = true else {
                                    cameraPermission.launch(Manifest.permission.CAMERA)
                                }
                            },
                            enabled = !state.isConverting,
                        )
                    }
                }

                if (state.images.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.dispatch(ConversionAction.PerPageChanged(state.perPage - 1)) },
                            enabled = !state.isConverting && state.perPage > 1,
                        ) { Text("−") }
                        Text(
                            stringResource(R.string.conversion_per_page, state.perPage),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        OutlinedButton(
                            onClick = { viewModel.dispatch(ConversionAction.PerPageChanged(state.perPage + 1)) },
                            enabled = !state.isConverting && state.perPage < 6,
                        ) { Text("＋") }
                    }
                    Text(
                        text = stringResource(
                            R.string.conversion_images_summary,
                            state.images.size,
                            PdfPageSpec.MAX_IMAGES,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        itemsIndexed(state.images) { index, image ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box {
                                    Thumbnail(
                                        uri = image.uri,
                                        modifier = Modifier
                                            .size(88.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                    )
                                    TextButton(
                                        onClick = { viewModel.dispatch(ConversionAction.RemoveImage(index)) },
                                        enabled = !state.isConverting,
                                    ) {
                                        Text("×", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                                if (index > 0) {
                                    TextButton(
                                        onClick = {
                                            viewModel.dispatch(ConversionAction.MoveImage(index, index - 1))
                                        },
                                        enabled = !state.isConverting,
                                    ) {
                                        Text(
                                            stringResource(R.string.conversion_move_up),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ConversionMode.TEXT -> {
                OutlinedButton(
                    onClick = { textPicker.launch(arrayOf("text/*", "application/octet-stream")) },
                    enabled = !state.isConverting,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(
                        if (state.text == null) {
                            stringResource(R.string.conversion_pick_text)
                        } else {
                            stringResource(R.string.conversion_repick)
                        },
                    )
                }
                state.text?.let {
                    Text(
                        text = stringResource(
                            R.string.conversion_text_meta,
                            it.displayName,
                            ByteFormat.formatBytes(it.bytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        message?.let { (textRes, isError) ->
            Text(
                text = stringResource(textRes),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        when (val phase = state.phase) {
            is ConversionPhase.Converting -> {
                LinearProgressIndicator(
                    progress = {
                        if (phase.total > 0) phase.current.toFloat() / phase.total else 0f
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Text(
                    text = stringResource(
                        R.string.conversion_generating,
                        phase.current,
                        phase.total,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = { viewModel.dispatch(ConversionAction.Cancel) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.conversion_cancel))
                }
            }

            else -> {
                FoolproofButton(
                    text = stringResource(R.string.conversion_start),
                    onClick = {
                        AdGateProvider.gate.gateOrRun(activity, FeatureId.FILE_CONVERSION) {
                            viewModel.dispatch(ConversionAction.Start)
                        }
                    },
                    enabled = state.canConvert,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        (state.phase as? ConversionPhase.Done)?.let { done ->
            PdfPreview(path = done.result.path, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = stringResource(
                    R.string.conversion_result_meta,
                    done.result.path.substringAfterLast('/'),
                    ByteFormat.formatBytes(done.result.bytes),
                    done.result.pageCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                OutlinedButton(onClick = { viewModel.dispatch(ConversionAction.Open) }) {
                    Text(stringResource(R.string.conversion_open))
                }
                OutlinedButton(onClick = { viewModel.dispatch(ConversionAction.Share) }) {
                    Text(stringResource(R.string.conversion_share))
                }
                if (state.canSaveToDownloads) {
                    OutlinedButton(onClick = { viewModel.dispatch(ConversionAction.SaveToDownloads) }) {
                        Text(stringResource(R.string.conversion_save_to_downloads))
                    }
                }
            }
        }
    }

    if (showCamera) {
        CameraCaptureDialog(
            onDismiss = { showCamera = false },
            onCaptured = { uri ->
                showCamera = false
                viewModel.dispatch(ConversionAction.ImagesPicked(listOf(uri)))
            },
        )
    }
}

@Composable
private fun Thumbnail(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, uri) }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}



private fun loadThumbnail(context: Context, uri: String): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(Uri.parse(uri), android.util.Size(200, 200), null)
    } else {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeStream(stream, null, bounds)
            val sample = maxOf(1, minOf(bounds.outWidth, bounds.outHeight) / 200)
            val options = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                android.graphics.BitmapFactory.decodeStream(it, null, options)
            }
        }
    }
}.getOrNull()

private fun Context.readFileMeta(uri: Uri): Pair<String, Long> = runCatching {
    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else "document.txt"
            val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else 0L
            name to size
        } else {
            "document.txt" to 0L
        }
    } ?: ("document.txt" to 0L)
}.getOrDefault("document.txt" to 0L)

/** 一次性提示 → 文案资源。 */
private fun ConversionEffect.MessageKind.labelRes(): Int = when (this) {
    ConversionEffect.MessageKind.DONE -> R.string.conversion_msg_done
    ConversionEffect.MessageKind.CANCELLED -> R.string.conversion_msg_cancelled
    ConversionEffect.MessageKind.NO_CONTENT -> R.string.conversion_msg_no_content
    ConversionEffect.MessageKind.UNREADABLE -> R.string.conversion_msg_unreadable
    ConversionEffect.MessageKind.UNSUPPORTED_ENCODING -> R.string.conversion_msg_unsupported_encoding
    ConversionEffect.MessageKind.OUT_OF_SPACE -> R.string.conversion_msg_out_of_space
    ConversionEffect.MessageKind.HEIC_UNSUPPORTED -> R.string.conversion_msg_heic_unsupported
    ConversionEffect.MessageKind.GENERATION_FAILED -> R.string.conversion_msg_generation_failed
    ConversionEffect.MessageKind.SAVED -> R.string.conversion_msg_saved
    ConversionEffect.MessageKind.SAVE_FAILED -> R.string.conversion_msg_save_failed
    ConversionEffect.MessageKind.SAVE_UNSUPPORTED -> R.string.conversion_msg_save_unsupported
    ConversionEffect.MessageKind.TOO_MANY_IMAGES -> R.string.conversion_msg_too_many_images
    ConversionEffect.MessageKind.TEXT_TOO_LARGE -> R.string.conversion_msg_text_too_large
}

private fun ConversionEffect.MessageKind.isError(): Boolean = when (this) {
    ConversionEffect.MessageKind.DONE,
    ConversionEffect.MessageKind.SAVED,
    -> false

    else -> true
}
/** 用唯一 FileProvider 分享 PDF。 */
private fun Context.sharePdf(path: String): Boolean {
    val uri = runCatching {
        FileProvider.getUriForFile(this, "$packageName.files", File(path))
    }.getOrNull() ?: return false
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching { startActivity(Intent.createChooser(intent, null)) }.isSuccess
}

/** 用系统 PDF 阅读器打开。 */
private fun Context.openPdf(path: String): Boolean {
    val uri = runCatching {
        FileProvider.getUriForFile(this, "$packageName.files", File(path))
    }.getOrNull() ?: return false
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return runCatching { startActivity(intent) }.isSuccess
}