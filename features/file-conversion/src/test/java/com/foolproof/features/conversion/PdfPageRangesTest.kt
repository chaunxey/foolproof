package com.foolproof.features.conversion

import com.foolproof.features.conversion.domain.PdfPageRanges
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageRangesTest {

    @Test
    fun `single pages and ranges keep order and dedupe`() {
        assertEquals(listOf(1, 2, 3, 5), PdfPageRanges.parse("1-3,5", 10))
        assertEquals(listOf(2, 3, 4), PdfPageRanges.parse(" 2 - 4 ", 10))
        assertEquals(listOf(1, 2, 3), PdfPageRanges.parse("1,1-3,2", 10))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `out of range page is rejected`() {
        PdfPageRanges.parse("11", 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reversed range is rejected`() {
        PdfPageRanges.parse("5-3", 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank spec is rejected`() {
        PdfPageRanges.parse("", 10)
    }
}
