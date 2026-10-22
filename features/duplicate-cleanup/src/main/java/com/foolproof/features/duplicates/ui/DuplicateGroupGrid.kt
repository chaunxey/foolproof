package com.foolproof.features.duplicates.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foolproof.base.util.ByteFormat
import com.foolproof.features.duplicates.DuplicateUiState
import com.foolproof.features.duplicates.R
import com.foolproof.features.duplicates.domain.PhotoGroup
import com.foolproof.features.duplicates.domain.PhotoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 结果区的懒加载贡献器（宪法 §3.2 v1.12）。
 *
 * 整页唯一的滚动/懒加载容器由 `:app` 持有（`LazyVerticalGrid`），本模块只往里塞 item：
 * 每个组头一行、每行最多 3 张图。
 *
 * **缩略图不经过 Coil 解码原图**：直接用 `ContentResolver.loadThumbnail`（API ≥ 29，走系统
 * 缩略图缓存）或采样解码（≤ 28）。实测这是掉帧的主因——用原图解码时每帧会触发 200ms 级尖峰。
 */
fun LazyGridScope.duplicatePhotoRows(
    state: DuplicateUiState,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var remaining = MAX_PHOTOS

    state.groups.forEach { group ->
        val visible = group.items.take(remaining.coerceAtLeast(0))
        remaining -= visible.size
        if (visible.isNotEmpty()) {
            item(key = "group-${group.id}", span = { GridItemSpan(maxLineSpan) }) {
                PhotoGroupCard(
                    group = group,
                    photos = visible,
                    selected = state.selected,
                    onToggle = onToggle,
                    onOpen = onOpen,
                )
            }
        }
    }

    if (remaining <= 0) {
        item(key = "group-limit-hint", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.duplicates_limit_hint, MAX_PHOTOS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

/** 同一组照片放在一个圆角卡片里，3 列等宽正方形，最后一行不拉伸。 */
@Composable
private fun PhotoGroupCard(
    group: PhotoGroup,
    photos: List<PhotoItem>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
            Text(
                text = stringResource(
                    R.string.duplicates_group_summary,
                    group.count,
                    ByteFormat.formatBytes(group.reclaimableBytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            photos.chunked(COLUMNS).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = GAP),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    row.forEach { photo ->
                        PhotoCell(
                            photo = photo,
                            checked = photo.id in selected,
                            onToggle = { onToggle(photo.id) },
                            onOpen = { onOpen(photo.id) },
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        )
                    }
                    // 最后一行不足 3 张时补空位，避免最后一张被拉满整行
                    repeat(COLUMNS - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(
    photo: PhotoItem,
    checked: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbnail = rememberThumbnail(photo.id)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        thumbnail?.let {
            Image(
                bitmap = it,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clickable(onClick = onOpen),
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
            shape = RoundedCornerShape(4.dp),
        ) {
            Text(
                text = ByteFormat.formatBytes(photo.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

/** 小尺寸缩略图，避免为网格格子解码原图（掉帧主因）。 */
@Composable
private fun rememberThumbnail(id: String): ImageBitmap? {
    val context = LocalContext.current

    // 懒加载网格会销毁滑出视口的 item，滑回来时若不缓存就要重新解码 → 反复卡顿
    ThumbnailCache.get(id)?.let { return it }

    var image by remember(id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(id) {
        ThumbnailCache.get(id)?.let {
            image = it
            return@LaunchedEffect
        }
        val loaded = withContext(Dispatchers.IO) {
            runCatching { loadThumbnail(context, id) }.getOrNull()?.asImageBitmap()
        }
        if (loaded != null) {
            ThumbnailCache.put(id, loaded)
            image = loaded
        }
    }
    return image
}

/**
 * 缩略图内存缓存（按字节数计）。容量取可用堆的 1/8，命中则直接复用，
 * 避免"滑出视口 → 销毁 → 滑回 → 重新解码"的循环。
 */
private object ThumbnailCache {

    private val maxBytes: Int
        get() = (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(MAX_CACHE_BYTES).toInt()

    private val cache = object : android.util.LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            value.width * value.height * BYTES_PER_PIXEL
    }

    fun get(id: String): ImageBitmap? = cache.get(id)

    fun put(id: String, bitmap: ImageBitmap) {
        cache.put(id, bitmap)
    }
}

private fun loadThumbnail(context: Context, id: String): Bitmap? {
    val uri = Uri.parse(id)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.contentResolver.loadThumbnail(uri, Size(THUMBNAIL_SIZE, THUMBNAIL_SIZE), null)
    } else {
        val options = BitmapFactory.Options().apply { inSampleSize = LEGACY_SAMPLE }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }
}

/** 宽高比，限制在合理范围内；尺寸缺失时退回 1:1。 */
private fun PhotoItem.safeAspect(): Float {
    if (width <= 0 || height <= 0) return 1f
    return (width.toFloat() / height.toFloat()).coerceIn(MIN_ASPECT, MAX_ASPECT)
}

private const val COLUMNS = 3
private const val MIN_ASPECT = 0.5f
private const val MAX_ASPECT = 2.2f
private const val THUMBNAIL_SIZE = 384
private const val LEGACY_SAMPLE = 4
private const val BYTES_PER_PIXEL = 4
private const val MAX_CACHE_BYTES = 48L * 1024 * 1024

/** 单次最多展示的缩略图数量：极端相册的保护。 */
private const val MAX_PHOTOS = 300

private val GAP = 6.dp
