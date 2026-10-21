package com.foolproof.features.conversion.ui

import android.content.Context
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
import androidx.compose.ui.unit.dp

import com.foolproof.base.util.ByteFormat
import com.foolproof.features.conversion.ConversionAction
import com.foolproof.features.conversion.ConversionEffect
import com.foolproof.features.conversion.ConversionMode
import com.foolproof.features.conversion.ConversionPhase
import com.foolproof.features.conversion.FileConversionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

/**
 * 文件转 PDF 的对外入口（宪法 §3：只提供 Section，页面由 `:app` 组装为独立 tab）。
 *
 * 本组件不自带滚动容器，滚动由 `:app` 提供。
 */
@Composable
fun FileConversionSection(
    modifier: Modifier = Modifier,
    viewModel: FileConversionViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

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
                is ConversionEffect.Message -> message = effect.kind.label() to effect.kind.isError()
                is ConversionEffect.Share -> if (!context.sharePdf(effect.path)) message = "没有可分享的应用" to true
                is ConversionEffect.Open -> if (!context.openPdf(effect.path)) message = "没有可打开 PDF 的应用" to true
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
            Text("文件转 PDF", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { viewModel.dispatch(ConversionAction.Reset) }) { Text("重置") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            FilterChip(
                selected = state.mode == ConversionMode.IMAGES,
                onClick = { viewModel.dispatch(ConversionAction.ModeChanged(ConversionMode.IMAGES)) },
                enabled = !state.isConverting,
                label = { Text("图片") },
            )
            FilterChip(
                selected = state.mode == ConversionMode.TEXT,
                onClick = { viewModel.dispatch(ConversionAction.ModeChanged(ConversionMode.TEXT)) },
                enabled = !state.isConverting,
                label = { Text("文本 / Markdown") },
            )
        }

        when (state.mode) {
            ConversionMode.IMAGES -> {
                OutlinedButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    enabled = !state.isConverting,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(if (state.images.isEmpty()) "选择图片" else "继续添加")
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
                        Text("每页 ${state.perPage} 张", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(
                            onClick = { viewModel.dispatch(ConversionAction.PerPageChanged(state.perPage + 1)) },
                            enabled = !state.isConverting && state.perPage < 6,
                        ) { Text("＋") }
                    }
                    Text(
                        text = "已选 ${state.images.size} 张（最多 50 张），点缩略图右上角可删除",
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
                                        Text("上移", style = MaterialTheme.typography.labelSmall)
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
                    Text(if (state.text == null) "选择文本 / Markdown" else "重新选择")
                }
                state.text?.let {
                    Text(
                        text = "${it.displayName} · ${ByteFormat.formatBytes(it.bytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        message?.let { (text, isError) ->
            Text(
                text = text,
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
                    text = "生成中 ${phase.current}/${phase.total} · 离开页面会取消",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                OutlinedButton(
                    onClick = { viewModel.dispatch(ConversionAction.Cancel) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("取消")
                }
            }

            else -> {
                Button(
                    onClick = { viewModel.dispatch(ConversionAction.Start) },
                    enabled = state.canConvert,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text("生成 PDF")
                }
            }
        }

        (state.phase as? ConversionPhase.Done)?.let { done ->
            PdfPreview(path = done.result.path, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "${done.result.path.substringAfterLast('/')} · " +
                    "${ByteFormat.formatBytes(done.result.bytes)} · ${done.result.pageCount} 页",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                OutlinedButton(onClick = { viewModel.dispatch(ConversionAction.Open) }) { Text("打开") }
                OutlinedButton(onClick = { viewModel.dispatch(ConversionAction.Share) }) { Text("分享") }
                if (state.canSaveToDownloads) {
                    OutlinedButton(onClick = { viewModel.dispatch(ConversionAction.SaveToDownloads) }) {
                        Text("存到下载")
                    }
                }
            }
        }
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

private fun ConversionEffect.MessageKind.label(): String = when (this) {
    ConversionEffect.MessageKind.DONE -> "PDF 已生成"
    ConversionEffect.MessageKind.CANCELLED -> "已取消"
    ConversionEffect.MessageKind.NO_CONTENT -> "没有可转换的内容"
    ConversionEffect.MessageKind.UNREADABLE -> "无法读取所选内容"
    ConversionEffect.MessageKind.UNSUPPORTED_ENCODING -> "文本编码不支持，请另存为 UTF-8"
    ConversionEffect.MessageKind.OUT_OF_SPACE -> "存储空间不足"
    ConversionEffect.MessageKind.HEIC_UNSUPPORTED -> "当前系统不支持 HEIC 格式，已跳过"
    ConversionEffect.MessageKind.GENERATION_FAILED -> "生成失败，请重试"
    ConversionEffect.MessageKind.SAVED -> "已保存到 下载/Foolproof"
    ConversionEffect.MessageKind.SAVE_FAILED -> "保存失败"
    ConversionEffect.MessageKind.SAVE_UNSUPPORTED -> "当前系统版本不支持直接存到下载"
    ConversionEffect.MessageKind.TOO_MANY_IMAGES -> "最多 50 张图片"
    ConversionEffect.MessageKind.TEXT_TOO_LARGE -> "文本文件过大（上限 2MB）"
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