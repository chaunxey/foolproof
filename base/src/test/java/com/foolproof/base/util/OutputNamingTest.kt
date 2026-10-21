package com.foolproof.base.util

import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputNamingTest {

    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `image compression name uses standard format`() {
        val name = OutputNaming.fileName(
            OutputNaming.MediaType.IMAGE,
            OutputNaming.Action.COMPRESS,
            "JPG",
            timestampMs = 0L,
            timeZone = utc,
        )

        assertEquals("Foolproof_IMG_COMP_19700101_000000.jpg", name)
    }

    @Test
    fun `video merge and pdf conversion names`() {
        assertEquals(
            "Foolproof_VID_MERGE_19700101_000000.mp4",
            OutputNaming.fileName(
                OutputNaming.MediaType.VIDEO,
                OutputNaming.Action.MERGE,
                "mp4",
                timestampMs = 0L,
                timeZone = utc,
            ),
        )
        assertEquals(
            "Foolproof_PDF_CONV_19700101_000000.pdf",
            OutputNaming.fileName(
                OutputNaming.MediaType.PDF,
                OutputNaming.Action.CONVERT,
                "pdf",
                timestampMs = 0L,
                timeZone = utc,
            ),
        )
    }

    @Test
    fun `index is zero padded`() {
        val name = OutputNaming.fileName(
            OutputNaming.MediaType.IMAGE,
            OutputNaming.Action.COMPRESS,
            "webp",
            timestampMs = 0L,
            index = 3,
            timeZone = utc,
        )

        assertEquals("Foolproof_IMG_COMP_19700101_000000_03.webp", name)
    }

    @Test
    fun `numbered keeps extension`() {
        val numbered = OutputNaming.numbered("Foolproof_IMG_COMP_19700101_000000.jpg", 2)

        assertEquals("Foolproof_IMG_COMP_19700101_000000_02.jpg", numbered)
    }

    @Test
    fun `name is ascii only`() {
        val name = OutputNaming.fileName(
            OutputNaming.MediaType.VIDEO,
            OutputNaming.Action.COMPRESS,
            "mp4",
            timestampMs = 0L,
            timeZone = utc,
        )

        assertTrue(name.all { it.code < 128 })
    }
}
