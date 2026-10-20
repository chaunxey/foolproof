package com.foolproof.features.duplicates.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.foolproof.base.util.ByteFormat
import com.foolproof.features.duplicates.domain.PhotoGroup
import com.foolproof.features.duplicates.domain.PhotoItem

/**
 * 分组网格。
 *
 * 宪法 §3.2（v1.11）：
 * - 页面滚动由 `:app` 统一管理，Section **不得自带滚动容器**，因此用普通布局渲染
 *   （`LazyVerticalGrid` 在 `verticalScroll` 父容器里会因高度无界崩溃）；
 * - 缩略图 MUST 完整显示整张图，禁止裁剪。
 *
 * 排版方式：**按原图宽高比**把一行 3 张图撑满整行宽度（不裁剪、不留黑边），
 * 行高由该行各图宽高比之和反推，极端比例会被夹到 [MIN_ASPECT]..[MAX_ASPECT] 以免单张图占满整屏。
 */
@Composable
fun DuplicateGroupGrid(
    groups: List<PhotoGroup>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var remaining = MAX_PHOTOS

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GAP)) {
        groups.forEach { group ->
            GroupHeader(group)

            val visible = group.items.take(remaining.coerceAtLeast(0))
            remaining -= visible.size

            JustifiedRows(
                photos = visible,
                selected = selected,
                onToggle = onToggle,
                onOpen = onOpen,
            )
        }

        if (remaining <= 0) {
            Text(
                text = "结果较多，仅显示前 $MAX_PHOTOS 张；删除后可重新查询继续",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun JustifiedRows(
    photos: List<PhotoItem>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val availableWidth: Dp = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
            photos.chunked(COLUMNS).forEach { row ->
                val aspects = row.map { it.safeAspect() }
                val gaps = GAP * (row.size - 1)
                // 行高 = 可用宽度 / 本行宽高比之和（保证每张图都完整、不被裁切）
                val rowHeight: Dp = (availableWidth - gaps) / aspects.sum()

                Row(modifier = Modifier.height(rowHeight)) {
                    row.forEachIndexed { index, photo ->
                        PhotoCell(
                            photo = photo,
                            checked = photo.id in selected,
                            onToggle = { onToggle(photo.id) },
                            onOpen = { onOpen(photo.id) },
                            modifier = Modifier
                                .width(rowHeight * aspects[index])
                                .height(rowHeight),
                        )
                        if (index != row.lastIndex) Spacer(modifier = Modifier.width(GAP))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: PhotoGroup) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(6.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${group.count} 张 · 可释放 ${ByteFormat.formatBytes(group.reclaimableBytes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        AsyncImage(
            model = photo.id,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().clickable(onClick = onOpen),
        )
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

/** 宽高比，限制在合理范围内；尺寸缺失时退回 1:1。 */
private fun PhotoItem.safeAspect(): Float {
    if (width <= 0 || height <= 0) return 1f
    return (width.toFloat() / height.toFloat()).coerceIn(MIN_ASPECT, MAX_ASPECT)
}

private const val COLUMNS = 3
private const val MIN_ASPECT = 0.5f
private const val MAX_ASPECT = 2.2f

/** 单次最多渲染的缩略图数量：本页不是懒加载容器，需要上限保护。 */
private const val MAX_PHOTOS = 120

private val GAP = 6.dp
