package com.foolproof.ads.api

import com.foolproof.base.ads.FeatureId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingAdGateTest {

    private val feature = FeatureId.IMAGE_COMPRESSION

    private class FakeUsage(var count: Int = 0) : AdUsage {
        override fun uses(feature: FeatureId): Int = count

        override fun increment(feature: FeatureId) {
            count += 1
        }

        override fun reset(feature: FeatureId) {
            count = 0
        }
    }

    private class FakeScheduler : DelayScheduler {
        var delayMs: Long = -1L
        var cancelled = false
        private var block: (() -> Unit)? = null

        override fun postDelayed(delayMs: Long, block: () -> Unit) {
            this.delayMs = delayMs
            this.block = block
        }

        override fun cancel() {
            cancelled = true
        }

        fun fire() {
            block?.invoke()
        }
    }

    private class FakeAd : FullscreenAd {
        var shown = false
        var destroyed = false
        private var closed: (() -> Unit)? = null

        override fun show(onClosed: () -> Unit) {
            shown = true
            closed = onClosed
        }

        override fun destroy() {
            destroyed = true
        }

        fun close() {
            closed?.invoke()
        }
    }

    private class FakeLoader(
        private val ad: FakeAd = FakeAd(),
        private val fail: Boolean = false,
        private val silent: Boolean = false,
        override val isConfigured: Boolean = true,
    ) : FullscreenAdLoader {
        var loadCalled = false

        override fun load(onLoaded: (FullscreenAd) -> Unit, onFailed: () -> Unit) {
            loadCalled = true
            when {
                silent -> Unit
                fail -> onFailed()
                else -> onLoaded(ad)
            }
        }
    }

    private fun gate(
        usage: FakeUsage,
        scheduler: FakeScheduler,
        config: AdGateConfig = AdGateConfig(),
    ) = CountingAdGate(usage, config) { scheduler }

    @Test
    fun `below threshold allows without loading`() {
        val usage = FakeUsage(count = 4)
        val scheduler = FakeScheduler()
        val loader = FakeLoader()
        var allowed = 0

        gate(usage, scheduler).maybeGate(feature, { allowed += 1 }, loader)

        assertEquals(1, allowed)
        assertFalse(loader.loadCalled)
        assertEquals(4, usage.count)
    }

    @Test
    fun `reaching threshold shows ad and resets after close`() {
        val usage = FakeUsage(count = 5)
        val scheduler = FakeScheduler()
        val ad = FakeAd()
        val loader = FakeLoader(ad = ad)
        var allowed = 0

        gate(usage, scheduler).maybeGate(feature, { allowed += 1 }, loader)

        assertTrue(loader.loadCalled)
        assertTrue(ad.shown)
        assertEquals(0, allowed)
        assertEquals(5_000L, scheduler.delayMs)

        ad.close()

        assertEquals(1, allowed)
        assertEquals(0, usage.count)
        assertTrue(ad.destroyed)
    }

    @Test
    fun `load failure allows and resets`() {
        val usage = FakeUsage(count = 5)
        val scheduler = FakeScheduler()
        val loader = FakeLoader(fail = true)
        var allowed = 0

        gate(usage, scheduler).maybeGate(feature, { allowed += 1 }, loader)

        assertEquals(1, allowed)
        assertEquals(0, usage.count)
        assertTrue(scheduler.cancelled)
    }

    @Test
    fun `timeout allows and resets`() {
        val usage = FakeUsage(count = 5)
        val scheduler = FakeScheduler()
        val loader = FakeLoader(silent = true)
        var allowed = 0

        gate(usage, scheduler).maybeGate(feature, { allowed += 1 }, loader)
        assertEquals(0, allowed)

        scheduler.fire()

        assertEquals(1, allowed)
        assertEquals(0, usage.count)
    }

    @Test
    fun `dismiss and timeout never callback twice`() {
        val usage = FakeUsage(count = 5)
        val scheduler = FakeScheduler()
        val ad = FakeAd()
        var allowed = 0

        gate(usage, scheduler).maybeGate(feature, { allowed += 1 }, FakeLoader(ad = ad))

        scheduler.fire()
        ad.close()
        scheduler.fire()

        assertEquals(1, allowed)
        assertTrue(ad.destroyed)
    }

    @Test
    fun `unconfigured resets without loading`() {
        val usage = FakeUsage(count = 5)
        val scheduler = FakeScheduler()
        val loader = FakeLoader(isConfigured = false)
        var allowed = 0

        gate(usage, scheduler).maybeGate(feature, { allowed += 1 }, loader)

        assertEquals(1, allowed)
        assertEquals(0, usage.count)
        assertFalse(loader.loadCalled)
        assertFalse(scheduler.cancelled)
    }

    @Test
    fun `onSuccess increments usage`() {
        val usage = FakeUsage(count = 2)
        val gate = gate(usage, FakeScheduler())

        gate.onSuccess(feature)

        assertEquals(3, usage.count)
    }
}
