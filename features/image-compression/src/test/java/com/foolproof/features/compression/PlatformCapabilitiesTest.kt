package com.foolproof.features.compression

import com.foolproof.features.compression.platform.PlatformCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformCapabilitiesTest {

    @Test
    fun `heic is not decodable on API 27`() {
        assertFalse(PlatformCapabilities(27).isHeicDecodable)
    }

    @Test
    fun `heic is decodable from API 28`() {
        assertTrue(PlatformCapabilities(28).isHeicDecodable)
        assertTrue(PlatformCapabilities(37).isHeicDecodable)
    }

    @Test
    fun `gallery write requires API 29`() {
        assertFalse(PlatformCapabilities(28).canSaveToGallery)
        assertTrue(PlatformCapabilities(29).canSaveToGallery)
    }

    @Test
    fun `lossy webp constant requires API 30`() {
        assertFalse(PlatformCapabilities(29).useLossyWebp)
        assertTrue(PlatformCapabilities(30).useLossyWebp)
    }
}
