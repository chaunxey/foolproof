package com.foolproof.ads.api

import com.foolproof.base.ads.FeatureId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RewardedGateTest {

    private val feature = FeatureId.IMAGE_COMPRESSION
    private val day = "2026-10-23"

    private class FakeUsage(var quota: Quota) : AdUsage {
        override fun quota(feature: FeatureId): Quota = quota

        override fun consume(feature: FeatureId) {
            quota = quota.consume()
        }

        override fun grantBonus(feature: FeatureId, amount: Int) {
            quota = quota.grant(amount)
        }
    }

    private class FakeScheduler : DelayScheduler {
        var cancelled = false
        private var block: (() -> Unit)? = null

        override fun postDelayed(delayMs: Long, block: () -> Unit) {
            this.block = block
        }

        override fun cancel() {
            cancelled = true
        }

        fun fire() {
            block?.invoke()
        }
    }

    private class FakeAd : RewardedAd {
        var shown = false
        var destroyed = false
        private var finished: ((Boolean) -> Unit)? = null

        override fun show(onFinished: (Boolean) -> Unit) {
            shown = true
            finished = onFinished
        }

        override fun destroy() {
            destroyed = true
        }

        fun finish(rewarded: Boolean) {
            finished?.invoke(rewarded)
        }
    }

    private class FakeLoader(
        private val ad: FakeAd = FakeAd(),
        private val fail: Boolean = false,
        private val silent: Boolean = false,
        override val isConfigured: Boolean = true,
    ) : RewardedAdLoader {
        var loadCalled = false

        override fun load(onLoaded: (RewardedAd) -> Unit, onFailed: () -> Unit) {
            loadCalled = true
            when {
                silent -> Unit
                fail -> onFailed()
                else -> onLoaded(ad)
            }
        }
    }

    private fun lockedUsage() = FakeUsage(Quota(day, freeUsed = 5))

    private fun gate(
        usage: AdUsage,
        loader: RewardedAdLoader,
        scheduler: FakeScheduler,
        clock: () -> Long = { 0L },
    ) = RewardedGate(usage, AdGateConfig(), { loader }, { scheduler }, clock)

    @Test
    fun `with quota remaining unlocks without loading ad`() {
        val usage = FakeUsage(Quota(day))
        val loader = FakeLoader()
        var allowed = false

        gate(usage, loader, FakeScheduler()).unlock(feature, loader) { allowed = it }

        assertTrue(allowed)
        assertFalse(loader.loadCalled)
    }

    @Test
    fun `watching reward grants five uses and allows`() {
        val usage = lockedUsage()
        val ad = FakeAd()
        var allowed = false

        gate(usage, FakeLoader(ad), FakeScheduler()).unlock(feature, FakeLoader(ad)) { allowed = it }
        assertTrue(ad.shown)

        ad.finish(true)

        assertTrue(allowed)
        assertEquals(5, usage.quota.bonus)
        assertTrue(ad.destroyed)
    }

    @Test
    fun `closing without reward does not grant or allow`() {
        val usage = lockedUsage()
        val ad = FakeAd()
        var allowed = true

        gate(usage, FakeLoader(ad), FakeScheduler()).unlock(feature, FakeLoader(ad)) { allowed = it }
        ad.finish(false)

        assertFalse(allowed)
        assertEquals(0, usage.quota.bonus)
    }

    @Test
    fun `load failure allows once without changing quota`() {
        val usage = lockedUsage()
        val loader = FakeLoader(fail = true)
        var allowed = false

        gate(usage, loader, FakeScheduler()).unlock(feature, loader) { allowed = it }

        assertTrue(allowed)
        assertEquals(0, usage.quota.bonus)
        assertEquals(5, usage.quota.freeUsed)
    }

    @Test
    fun `failure cooldown allows immediately without loading`() {
        var now = 0L
        val usage = lockedUsage()
        val scheduler = FakeScheduler()
        val gate = gate(usage, FakeLoader(fail = true), scheduler) { now }

        gate.unlock(feature, FakeLoader(fail = true)) { }
        val secondLoader = FakeLoader()
        var allowed = false
        gate.unlock(feature, secondLoader) { allowed = it }

        assertTrue(allowed)
        assertFalse(secondLoader.loadCalled)
    }

    @Test
    fun `after cooldown it tries to load again`() {
        var now = 0L
        val usage = lockedUsage()
        val gate = gate(usage, FakeLoader(fail = true), FakeScheduler()) { now }

        gate.unlock(feature, FakeLoader(fail = true)) { }

        now = 61_000L
        val secondLoader = FakeLoader()
        gate.unlock(feature, secondLoader) { }

        assertTrue(secondLoader.loadCalled)
    }

    @Test
    fun `unconfigured ad allows without loading`() {
        val usage = lockedUsage()
        val loader = FakeLoader(isConfigured = false)
        var allowed = false

        gate(usage, loader, FakeScheduler()).unlock(feature, loader) { allowed = it }

        assertTrue(allowed)
        assertFalse(loader.loadCalled)
    }

    @Test
    fun `timeout allows once and starts cooldown`() {
        val usage = lockedUsage()
        val scheduler = FakeScheduler()
        val gate = gate(usage, FakeLoader(silent = true), scheduler)
        var allowed = false

        gate.unlock(feature, FakeLoader(silent = true)) { allowed = it }
        assertFalse(allowed)

        scheduler.fire()

        assertTrue(allowed)
        assertEquals(0, usage.quota.bonus)

        val secondLoader = FakeLoader()
        gate.unlock(feature, secondLoader) { }
        assertFalse(secondLoader.loadCalled)
    }

    @Test
    fun `late callbacks never fire twice`() {
        val usage = lockedUsage()
        val ad = FakeAd()
        var calls = 0

        gate(usage, FakeLoader(ad), FakeScheduler()).unlock(feature, FakeLoader(ad)) { calls += 1 }
        ad.finish(true)
        ad.finish(false)

        assertEquals(1, calls)
        assertEquals(5, usage.quota.bonus)
    }

    @Test
    fun `success consumes one use`() {
        val usage = FakeUsage(Quota(day))
        val gate = gate(usage, FakeLoader(), FakeScheduler())

        gate.onSuccess(feature)

        assertEquals(1, usage.quota.freeUsed)
        assertEquals(4, usage.quota.available)
    }
}
