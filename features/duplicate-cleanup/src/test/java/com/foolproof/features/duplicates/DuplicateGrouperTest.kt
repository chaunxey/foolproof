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
        luma: List<Int> = emptyList(),
    ) = PhotoItem(
        id = id,
        displayName = "$id.jpg",
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        hash = hash,
        lowInformation = lowInformation,
        luma = luma,
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

        assertTrue("距离 6 且没有灰度签名时不应合并", groups.isEmpty())
    }

    @Test
    fun `recompressed copy with slightly different hash groups by luma correlation`() {
        val luma = (0 until 72).map { (it * 7) % 256 }
        val groups = DuplicateGrouper.group(
            listOf(
                item("original", hash = 0L, luma = luma),
                item("compressed", hash = hashWithDistance(8), luma = luma),
            ),
            threshold = 5,
        )

        assertEquals(1, groups.size)
        assertEquals(2, groups.single().count)
    }

    @Test
    fun `different images with close hash but different luma stay separate`() {
        val a = (0 until 72).map { it % 32 }
        val b = (0 until 72).map { 255 - (it % 32) }
        val groups = DuplicateGrouper.group(
            listOf(
                item("a", hash = 0L, luma = a),
                item("b", hash = hashWithDistance(8), luma = b),
            ),
            threshold = 5,
        )

        assertTrue("灰度结构不同不能合并", groups.isEmpty())
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
    fun `identical copies always land in the same group`() {
        // 回归测试（v1.14）：实测发现字节完全相同的两张图曾被分到不同组。
        val groups = DuplicateGrouper.group(
            listOf(
                item("a", 0b000000L),
                item("b", 0b111111L), // 与 a 距离 6 → 独立成组
                item("c1", 0b000011L), // 与 a 距离 2
                item("c2", 0b000011L), // 与 c1 完全相同
            ),
        )

        val groupWithC = groups.firstOrNull { g -> g.items.any { it.id == "c1" } }
        assertTrue("c1 必须成组", groupWithC != null)
        assertTrue(
            "完全相同的 c1/c2 必须在同一组",
            groupWithC!!.items.map { it.id }.containsAll(listOf("c1", "c2")),
        )
    }

    @Test
    fun `exact duplicate joins its twin even when representative is far`() {
        // 组代表是 a（与 z 距离 5，刚过阈值内），z 的副本 z2 与 a 距离 5 但仍应与 z 同组。
        // 更强的情形：z2 与代表距离超过阈值也必须因"哈希完全相同"而并入。
        val groups = DuplicateGrouper.group(
            listOf(
                item("a", 0b000000L),
                item("b", 0b000000L),   // a、b 同组，代表 = a
                item("z", 0b111100L),   // 与 a 距离 4 → 并入同一组
                item("z2", 0b111100L),  // z 的完全副本
            ),
        )

        val single = groups.singleOrNull()
        assertTrue("应只有一组", single != null)
        assertTrue(
            "z2 必须与 z 同组",
            single!!.items.map { it.id }.containsAll(listOf("z", "z2")),
        )
    }

    @Test
    fun `similar but distinct items do not chain into a mega group`() {
        // 防链式合并回归：a-b 相近、b-c 相近、c-d 相近，但 d 与代表 a 距离 > 阈值。
        // d = 0b11111100（6 位）与 a 距离 6（超阈值 5），与 c（0b00011100，3 位）距离 3。
        val a = item("a", 0b00000000L)
        val b = item("b", 0b00000111L) // 距 a 3
        val c = item("c", 0b00011100L) // 距 b 4
        val d = item("d", 0b11111100L) // 距 c 3，距 a 6（超阈值）

        val groups = DuplicateGrouper.group(listOf(a, b, c, d), threshold = 5)
        val groupWithD = groups.firstOrNull { g -> g.items.any { it.id == "d" } }

        assertTrue("d 不应与 a 同组", groupWithD == null || groupWithD.items.none { it.id == "a" })
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
