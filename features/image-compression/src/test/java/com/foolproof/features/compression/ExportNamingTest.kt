package com.foolproof.features.compression

import com.foolproof.features.compression.data.ExportNaming
import com.foolproof.features.compression.domain.CompressionOption
import com.foolproof.features.compression.domain.OutputFormat
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExportNamingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `file name uses base name quality and extension`() {
        val name = ExportNaming.fileName("photo.png", CompressionOption(OutputFormat.JPEG, 80))

        assertEquals("photo_q80.jpg", name)
    }

    @Test
    fun `webp extension is used for webp`() {
        val name = ExportNaming.fileName("photo.png", CompressionOption(OutputFormat.WEBP, 60))

        assertEquals("photo_q60.webp", name)
    }

    @Test
    fun `path separators and illegal characters are stripped`() {
        assertEquals("a_b_q80.jpg", ExportNaming.fileName("a:b.jpg", CompressionOption()))
        assertEquals("evil_q80.jpg", ExportNaming.fileName("../evil.jpg", CompressionOption()))
    }

    @Test
    fun `blank name falls back to image`() {
        assertEquals("image_q80.jpg", ExportNaming.fileName(".jpg", CompressionOption()))
        assertEquals("image_q80.jpg", ExportNaming.fileName("", CompressionOption()))
    }

    @Test
    fun `unique file appends index on conflict`() {
        val dir = tempFolder.newFolder("compressed")
        val option = CompressionOption()
        val first = ExportNaming.uniqueFile(dir, "photo.jpg", option)
        first.createNewFile()

        val second = ExportNaming.uniqueFile(dir, "photo.jpg", option)
        val secondFile = File(dir, second.name)
        secondFile.createNewFile()
        val third = ExportNaming.uniqueFile(dir, "photo.jpg", option)

        assertNotEquals(first.name, second.name)
        assertEquals("photo_q80_1.jpg", second.name)
        assertEquals("photo_q80_2.jpg", third.name)
    }
}
