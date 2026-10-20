package com.foolproof.features.duplicates

import com.foolproof.features.duplicates.domain.DuplicateGrouper
import com.foolproof.features.duplicates.domain.PhotoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateGrouperTest {

    private fun item(
        id: String,
        hash: Long,
        sizeBytes: Long = 1_000L,
        width: Int = 4000,
        height: Int = 3000,
        lowInformation: Boolean = false,
    ) = PhotoItem(
        id = id,
        displayName = "$id.jpg",
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        hash = hash,
        lowInformation = lowInformation,
    )

    /** 构造与基准哈希距离恰好为 [bits] 的哈希。 */
    private fun hashWithDistance(bits: Int): Long {
        var hash = 0L
        repeat(bits) { index -> hash = hash or (1L shl index) }
        return hash
    }

    @Test
    fun `distance exactly at threshold groups together`() {
        val groups = DuplicateGrouper.group(
            listOf(item("a", 0L), item("b", hashWithDistance(5))),
            threshold = 5,
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().count)
    }

    @Test
    fun `distance beyond threshold stays separate`() {
        val groups = DuplicateGrouper.group(
            listOf(item("a", 0L), item("b", hashWithDistance(6))),
            threshold = 5,
        )

        assertTrue("距离 6 不应合并", groups.isEmpty())
    }

    @Test
    fun `same photo at different qualities lands in one group`() {
        // 同图不同压缩 → 哈希几乎一致（这里模拟完全一致）
        val groups = DuplicateGrouper.group(
            listOf(
                item("original", hash = 0x1234L, sizeBytes = 4_000_000),
                item("q80", hash = 0x1234L, sizeBytes = 800_000),
                item("q50", hash = 0x1234L, sizeBytes = 300_000),
            ),
        )

        assertEquals(1, groups.size)
        assertEquals(3, groups.single().count)
        assertEquals(4_000_000L, groups.single().largest.sizeBytes)
        assertEquals(1_100_000L, groups.single().reclaimableBytes)
    }

    @Test
    fun `different compositions are not merged`() {
        val groups = DuplicateGrouper.group(
            listOf(item("a", 0L), item("b", hashWithDistance(20)), item("c", hashWithDistance(6))),
        )

        assertTrue("构图不同的照片不应进入同一组", groups.isEmpty())
    }

    @Test
    fun `solid color photos with different aspect ratios are not merged`() {
        // 纯色图哈希都是 0、距离 0，但宽高比不同 → 必须分开
        val groups = DuplicateGrouper.group(
            listOf(
                item("white-portrait", hash = 0L, width = 1080, height = 1920, lowInformation = true),
                item("white-landscape", hash = 0L, width = 1920, height = 1080, lowInformation = true),
            ),
        )

        assertTrue("宽高比不同的纯色图不得合并", groups.isEmpty())
    }

    @Test
    fun `solid color photos with same aspect and size are merged`() {
        // 同宽高比、同体积的纯色图视为重复（残留误判风险，已在文档注明）
        val groups = DuplicateGrouper.group(
            listOf(
                item("s1", hash = 0L, sizeBytes = 100_000, lowInformation = true),
                item("s2", hash = 0L, sizeBytes = 100_500, lowInformation = true),
            ),
        )

        assertEquals(1, groups.size)
    }

    @Test
    fun `single photos are not reported as groups`() {
        val groups = DuplicateGrouper.group(
            listOf(item("lonely", 0L), item("other", hashWithDistance(30))),
        )

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `default selection keeps the largest item`() {
        val group = DuplicateGrouper.group(
            listOf(
                item("small", 0L, sizeBytes = 100L),
                item("large", 0L, sizeBytes = 900L),
                item("medium", 0L, sizeBytes = 400L),
            ),
        ).single()

        val selection = DuplicateGrouper.defaultSelection(group)

        assertEquals(setOf("small", "medium"), selection)
        assertTrue("体积最大的一张必须默认保留", "large" !in selection)
    }

    @Test
    fun `default selection for many groups is the union`() {
        val groups = DuplicateGrouper.group(
            listOf(
                item("a1", 0L, sizeBytes = 100L),
                item("a2", 0L, sizeBytes = 200L),
                item("b1", hashWithDistance(40), sizeBytes = 300L),
                item("b2", hashWithDistance(40), sizeBytes = 400L),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals(setOf("a1", "b1"), DuplicateGrouper.defaultSelection(groups))
    }
}
