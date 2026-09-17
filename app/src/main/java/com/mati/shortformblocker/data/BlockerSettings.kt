package com.mati.shortformblocker.data

import com.mati.shortformblocker.detect.BlockRule
import com.mati.shortformblocker.detect.FeedBudget
import com.mati.shortformblocker.detect.RuleCatalog

/** A disable request that has been made but has not served its cooldown yet. */
data class PendingDisable(
    /** [TARGET_ALL] or a rule id. */
    val target: String,
    val requestedAt: Long,
    val effectiveAt: Long,
) {
    fun isDue(now: Long): Boolean = now >= effectiveAt

    fun remainingMillis(now: Long): Long = (effectiveAt - now).coerceAtLeast(0L)

    companion object {
        const val TARGET_ALL = "__all__"
    }
}

/** A feed budget change that loosens the rules, waiting out its cooldown before it counts. */
data class PendingFeedBudget(
    val budget: FeedBudget,
    val requestedAt: Long,
    val effectiveAt: Long,
) {
    fun isDue(now: Long): Boolean = now >= effectiveAt

    fun remainingMillis(now: Long): Long = (effectiveAt - now).coerceAtLeast(0L)
}

/**
 * Settings as stored, plus the rules about *when* a stored value takes effect.
 *
 * Turning protection off is never immediate: it becomes a [PendingDisable] that only applies once
 * the cooldown expires. Turning it back on is always immediate. Everything here is a pure function
 * of the stored state and the current time, so both the UI and the accessibility service can read
 * the same truth without coordinating.
 */
data class BlockerSettings(
    val protectionEnabled: Boolean = true,
    val disabledRuleIds: Set<String> = RuleCatalog.DEFAULT_DISABLED_IDS,
    val pending: PendingDisable? = null,
    val cooldownMinutes: Int = DEFAULT_COOLDOWN_MINUTES,
    val feedBudget: FeedBudget = FeedBudget(),
    val pendingFeedBudget: PendingFeedBudget? = null,
) {
    /** The budget in force right now: a looser one only counts once its cooldown has expired. */
    fun feedBudgetAt(now: Long): FeedBudget =
        pendingFeedBudget?.takeIf { it.isDue(now) }?.budget ?: feedBudget

    fun isProtectionOn(now: Long): Boolean {
        if (!protectionEnabled) return false
        val pendingAll = pending?.takeIf { it.target == PendingDisable.TARGET_ALL } ?: return true
        return !pendingAll.isDue(now)
    }

    fun isRuleOn(ruleId: String, now: Long): Boolean {
        if (ruleId in disabledRuleIds) return false
        val pendingRule = pending?.takeIf { it.target == ruleId } ?: return true
        return !pendingRule.isDue(now)
    }

    /** The rules the accessibility service should enforce at [now]. */
    fun activeRules(now: Long): List<BlockRule> {
        if (!isProtectionOn(now)) return emptyList()
        return RuleCatalog.ALL.filter { isRuleOn(it.id, now) }
    }

    fun pendingFor(target: String): PendingDisable? = pending?.takeIf { it.target == target }

    companion object {
        const val DEFAULT_COOLDOWN_MINUTES = 120
    }
}
