package com.foolproof.features.conversion.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.foolproof.features.conversion.R
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
    var pages by remember(path) { mutableStateOf<List<Bitmap>>(emptyList()) }

    LaunchedEffect(path) {
        pages = withContext(Dispatchers.IO) { renderPages(path) }
    }

    if (pages.isEmpty()) return

    // 所有页横向排列：多张图片时用户能一眼确认"页数对、顺序对"
    LazyRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(pages) { index, bitmap ->
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(
                        R.string.conversion_preview_page_desc,
                        index + 1,
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(240.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(androidx.compose.ui.graphics.Color.White),
                )
                Text(
                    text = stringResource(
                        R.string.conversion_preview_page_label,
                        index + 1,
                        pages.size,
                    ),
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** 用 PdfRenderer 渲染首页；失败返回 null（例如文件损坏）。 */
private fun renderPages(path: String): List<Bitmap> = runCatching {
    val file = File(path)
    if (!file.exists()) return emptyList()
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            (0 until renderer.pageCount).map { index ->
                renderer.openPage(index).use { page ->
                    Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        // PdfRenderer 不铺白底，不填会得到透明/黑色
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }
}.getOrDefault(emptyList())
