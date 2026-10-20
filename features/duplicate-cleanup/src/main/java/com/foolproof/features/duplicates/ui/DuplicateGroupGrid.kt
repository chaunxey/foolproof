package com.foolproof.features.duplicates.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import coil3.compose.AsyncImage
import com.foolproof.base.util.ByteFormat
import com.foolproof.features.duplicates.domain.PhotoGroup
import com.foolproof.features.duplicates.domain.PhotoItem

/**
 * 分组网格：跨列的组头 + 每张图的缩略图与勾选框（宪法 §3.2 要求 `LazyVerticalGrid` + 跨列组头）。
 */
@Composable
fun DuplicateGroupGrid(
    groups: List<PhotoGroup>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { group ->
            item(
                key = "header-${group.id}",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                GroupHeader(group)
            }
            items(group.items, key = { it.id }) { photo ->
                PhotoCell(
                    photo = photo,
                    checked = photo.id in selected,
                    onToggle = { onToggle(photo.id) },
                    onOpen = { onOpen(photo.id) },
                )
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
) {
    Box(modifier = Modifier.aspectRatio(1f)) {
        AsyncImage(
            model = photo.id,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onOpen),
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
