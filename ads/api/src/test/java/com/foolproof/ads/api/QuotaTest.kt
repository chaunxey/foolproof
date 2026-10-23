package com.foolproof.ads.api

import org.junit.Assert.assertEquals
import org.junit.Test

class QuotaTest {

    private val day = "2026-10-23"

    @Test
    fun `fresh day has five free uses`() {
        val quota = Quota(day)

        assertEquals(5, quota.freeRemaining)
        assertEquals(5, quota.available)
    }

    @Test
    fun `consume uses free quota first`() {
        var quota = Quota(day).grant(5)
        repeat(5) { quota = quota.consume() }

        assertEquals(0, quota.freeRemaining)
        assertEquals(5, quota.bonus)

        quota = quota.consume()

        assertEquals(5, quota.freeUsed)
        assertEquals(4, quota.bonus)
    }

    @Test
    fun `next day resets free quota but keeps rewards`() {
        var quota = Quota(day).grant(7)
        repeat(3) { quota = quota.consume() }

        val nextDay = quota.rollTo("2026-10-24")

        assertEquals(0, nextDay.freeUsed)
        assertEquals(7, nextDay.bonus)
        assertEquals(12, nextDay.available)
    }

    @Test
    fun `unused free uses do not accumulate`() {
        var quota = Quota(day)
        repeat(2) { quota = quota.consume() }

        val nextDay = quota.rollTo("2026-10-24")

        assertEquals(5, nextDay.freeRemaining)
        assertEquals(5, nextDay.available)
    }

    @Test
    fun `rewards stack and are repeatable`() {
        val quota = Quota(day).grant(5).grant(5)

        assertEquals(10, quota.bonus)
        assertEquals(15, quota.available)
    }

    @Test
    fun `same day roll is no-op`() {
        val quota = Quota(day).consume()

        assertEquals(quota, quota.rollTo(day))
    }
}
