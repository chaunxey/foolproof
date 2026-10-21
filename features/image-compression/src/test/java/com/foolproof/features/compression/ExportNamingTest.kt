package com.foolproof.features.compression

import com.foolproof.base.util.OutputNaming
import com.foolproof.features.compression.data.ExportNaming
import com.foolproof.features.compression.domain.CompressionOption
import com.foolproof.features.compression.domain.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportNamingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `file name follows the standard format`() {
        val name = ExportNaming.fileName(CompressionOption(OutputFormat.JPEG, 80), timestampMs = 0L)

        assertTrue(name.matches(Regex("Foolproof_IMG_COMP_\\d{8}_\\d{6}\\.jpg")))
    }

    @Test
    fun `webp extension is used for webp`() {
        val name = ExportNaming.fileName(CompressionOption(OutputFormat.WEBP, 60), timestampMs = 0L)

        assertTrue(name.endsWith(".webp"))
    }

    @Test
    fun `unique file appends zero padded index on conflict`() {
        val dir = tempFolder.newFolder("compressed")
        val option = CompressionOption()
        val base = ExportNaming.fileName(option, timestampMs = 0L)

        val first = ExportNaming.uniqueFile(dir, option, timestampMs = 0L)
        first.createNewFile()
        val second = ExportNaming.uniqueFile(dir, option, timestampMs = 0L)
        second.createNewFile()
        val third = ExportNaming.uniqueFile(dir, option, timestampMs = 0L)

        assertEquals(base, first.name)
        assertEquals(OutputNaming.numbered(base, 2), second.name)
        assertEquals(OutputNaming.numbered(base, 3), third.name)
    }
}
