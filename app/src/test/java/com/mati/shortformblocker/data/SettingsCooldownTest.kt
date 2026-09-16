package com.mati.shortformblocker.data

import com.mati.shortformblocker.detect.RuleCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class SettingsCooldownTest {

    private val now = 1_700_000_000_000L
    private val twoHours = TimeUnit.HOURS.toMillis(2)

    @Test
    fun `protection stays on while the cooldown runs`() {
        val settings = BlockerSettings(
            pending = PendingDisable(PendingDisable.TARGET_ALL, now, now + twoHours),
        )
        assertTrue(settings.isProtectionOn(now))
        assertTrue(settings.isProtectionOn(now + twoHours - 1))
        assertTrue(settings.activeRules(now).isNotEmpty())
    }

    @Test
    fun `protection goes off once the cooldown expires`() {
        val settings = BlockerSettings(
            pending = PendingDisable(PendingDisable.TARGET_ALL, now, now + twoHours),
        )
        assertFalse(settings.isProtectionOn(now + twoHours))
        assertTrue(settings.activeRules(now + twoHours).isEmpty())
    }

    @Test
    fun `a pending rule disable only affects that rule`() {
        val settings = BlockerSettings(
            pending = PendingDisable(RuleCatalog.YOUTUBE_SHORTS.id, now, now + twoHours),
        )
        assertTrue(settings.isRuleOn(RuleCatalog.YOUTUBE_SHORTS.id, now))
        assertFalse(settings.isRuleOn(RuleCatalog.YOUTUBE_SHORTS.id, now + twoHours))
        assertTrue(settings.isRuleOn(RuleCatalog.INSTAGRAM_REELS.id, now + twoHours))
        assertTrue(settings.isProtectionOn(now + twoHours))
    }

    @Test
    fun `rules that are off by default are not active`() {
        val active = BlockerSettings().activeRules(now).map { it.id }.toSet()
        assertEquals(emptySet<String>(), active.intersect(RuleCatalog.DEFAULT_DISABLED_IDS))
        assertTrue(RuleCatalog.YOUTUBE_SHORTS.id in active)
    }

    @Test
    fun `countdown is reported as remaining time`() {
        val pending = PendingDisable(PendingDisable.TARGET_ALL, now, now + twoHours)
        assertEquals(twoHours, pending.remainingMillis(now))
        assertEquals(0L, pending.remainingMillis(now + twoHours + 5_000))
    }

    @Test
    fun `stats counters round trip`() {
        val counts = mapOf("2026-09-16" to 4, "2026-09-15" to 11)
        assertEquals(counts, StatsRepository.parseCounts(StatsRepository.formatCounts(counts)))
        assertEquals(emptyMap<String, Int>(), StatsRepository.parseCounts(null))
        assertEquals(emptyMap<String, Int>(), StatsRepository.parseCounts("garbage"))
    }
}
