package com.foolproof.features.conversion.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用内 PDF 预览（首页）。
 *
 * 为什么必须有：不少设备（含 AOSP 模拟器）没有 PDF 阅读器，【打开】只能失败；
 * 用户至少要在应用里看到"生成出来的东西长什么样"，否则无法确认结果正确。
 */
@Composable
internal fun PdfPreview(path: String, modifier: Modifier = Modifier) {
    var page by remember(path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path) {
        page = withContext(Dispatchers.IO) { renderFirstPage(path) }
    }

    page?.let { bitmap ->
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "PDF 首页预览",
            contentScale = ContentScale.Fit,
            modifier = modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color.White),
        )
    }
}

/** 用 PdfRenderer 渲染首页；失败返回 null（例如文件损坏）。 */
private fun renderFirstPage(path: String): Bitmap? = runCatching {
    val file = File(path)
    if (!file.exists()) return null
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (renderer.pageCount <= 0) return null
            renderer.openPage(0).use { page ->
                val scale = 2
                Bitmap.createBitmap(
                    page.width * scale,
                    page.height * scale,
                    Bitmap.Config.ARGB_8888,
                ).also { bitmap ->
                    // PdfRenderer 不铺白底，不填会得到透明/黑色
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}.getOrNull()
