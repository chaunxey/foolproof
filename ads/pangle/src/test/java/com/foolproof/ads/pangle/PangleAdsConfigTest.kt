package com.foolproof.ads.pangle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PangleAdsConfigTest {

    @Test
    fun `shipped config is configured`() {
        assertTrue(PangleAdsConfig.configured)
    }

    @Test
    fun `empty app id disables ads`() {
        assertFalse(PangleAdsConfig.isConfigured("", "banner", "interstitial"))
    }

    @Test
    fun `empty banner disables ads`() {
        assertFalse(PangleAdsConfig.isConfigured("5000000", "", "interstitial"))
    }

    @Test
    fun `empty interstitial disables ads`() {
        assertFalse(PangleAdsConfig.isConfigured("5000000", "banner", ""))
    }

    @Test
    fun `all ids configured enables ads`() {
        assertTrue(PangleAdsConfig.isConfigured("5000000", "banner", "interstitial"))
    }
}
