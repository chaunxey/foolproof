package com.foolproof.features.compression

import com.foolproof.features.compression.domain.CompressionOption
import com.foolproof.features.compression.domain.OutputFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CompressionOptionTest {

    @Test
    fun `default is JPEG with quality 80`() {
        val option = CompressionOption.Default

        assertEquals(OutputFormat.JPEG, option.format)
        assertEquals(80, option.quality)
        assertEquals("jpg", option.extension)
    }

    @Test
    fun `webp extension is webp`() {
        assertEquals("webp", CompressionOption(format = OutputFormat.WEBP).extension)
    }

    @Test
    fun `quality boundaries are accepted`() {
        assertEquals(0, CompressionOption(quality = 0).quality)
        assertEquals(100, CompressionOption(quality = 100).quality)
    }

    @Test
    fun `out of range quality is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { CompressionOption(quality = -1) }
        assertThrows(IllegalArgumentException::class.java) { CompressionOption(quality = 101) }
    }

    @Test
    fun `withQuality clamps instead of throwing`() {
        assertEquals(100, CompressionOption.Default.withQuality(999).quality)
        assertEquals(0, CompressionOption.Default.withQuality(-5).quality)
        assertEquals(55, CompressionOption.Default.withQuality(55).quality)
    }

    @Test
    fun `withFormat switches output format`() {
        val option = CompressionOption.Default.withFormat(OutputFormat.WEBP)

        assertEquals(OutputFormat.WEBP, option.format)
        assertEquals("webp", option.extension)
    }
}
