package com.mati.shortformblocker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mati.shortformblocker.detect.FeedBudget
import com.mati.shortformblocker.detect.RuleCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

internal val Context.blockerDataStore: DataStore<Preferences> by preferencesDataStore(name = "blocker")

/**
 * Stored settings plus the cooldown state machine.
 *
 * Only one disable request can be pending at a time; asking to disable something else replaces it
 * and restarts the cooldown, which is the conservative direction.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<BlockerSettings> = context.blockerDataStore.data.map { it.toSettings() }

    suspend fun current(): BlockerSettings = settings.first()

    /** Starts the cooldown. The switch keeps showing "on" until [applyDuePending] lets it through. */
    suspend fun requestDisable(target: String, now: Long = System.currentTimeMillis()) {
        context.blockerDataStore.edit { prefs ->
            val cooldown = prefs[KEY_COOLDOWN_MINUTES] ?: BlockerSettings.DEFAULT_COOLDOWN_MINUTES
            prefs[KEY_PENDING_TARGET] = target
            prefs[KEY_PENDING_REQUESTED_AT] = now
            prefs[KEY_PENDING_EFFECTIVE_AT] = now + TimeUnit.MINUTES.toMillis(cooldown.toLong())
        }
    }

    suspend fun cancelPending() {
        context.blockerDataStore.edit { it.clearPending() }
    }

    /** Re-enabling is instant, and clears any pending request aimed at the same target. */
    suspend fun enable(target: String) {
        context.blockerDataStore.edit { prefs ->
            // Always write the set out. Setting INITIALIZED without it used to make the stored
            // "nothing disabled" and "never touched" states indistinguishable, which silently
            // switched on the rules that ship disabled for being false-positive prone.
            val disabled = prefs.disabledRules()
            prefs[KEY_DISABLED_RULES] = if (target == PendingDisable.TARGET_ALL) {
                prefs[KEY_PROTECTION_ENABLED] = true
                disabled
            } else {
                disabled - target
            }
            if (prefs[KEY_PENDING_TARGET] == target) prefs.clearPending()
            prefs[KEY_INITIALIZED] = true
        }
    }

    /**
     * Applies a pending disable whose cooldown has expired. Called on a timer by
     * [com.mati.shortformblocker.service.ProtectionService] and whenever the UI is on screen.
     */
    suspend fun applyDuePending(now: Long = System.currentTimeMillis()) {
        context.blockerDataStore.edit { prefs ->
            val target = prefs[KEY_PENDING_TARGET] ?: return@edit
            val effectiveAt = prefs[KEY_PENDING_EFFECTIVE_AT] ?: return@edit
            if (now < effectiveAt) return@edit
            if (target == PendingDisable.TARGET_ALL) {
                prefs[KEY_PROTECTION_ENABLED] = false
            } else {
                prefs[KEY_DISABLED_RULES] = prefs.disabledRules() + target
            }
            prefs[KEY_INITIALIZED] = true
            prefs.clearPending()
        }
        applyDueFeedBudget(now)
    }

    /** The feed-budget twin of [applyDuePending]: a loosening that has served its cooldown lands. */
    private suspend fun applyDueFeedBudget(now: Long) {
        context.blockerDataStore.edit { prefs ->
            val effectiveAt = prefs[KEY_PENDING_FEED_EFFECTIVE_AT] ?: return@edit
            if (now < effectiveAt) return@edit
            prefs[KEY_FEED_SCREENS] = prefs[KEY_PENDING_FEED_SCREENS] ?: return@edit
            prefs[KEY_FEED_RESET_MINUTES] = prefs[KEY_PENDING_FEED_RESET_MINUTES] ?: return@edit
            prefs.clearPendingFeedBudget()
        }
    }

    /**
     * Tightening the feed budget - fewer screens, or a longer wait before it refills - applies at
     * once. Loosening it is a disable by another name, so it waits out the same cooldown, and the
     * budget in force until then is the old one.
     */
    suspend fun setFeedBudget(requested: FeedBudget, now: Long = System.currentTimeMillis()) {
        val budget = requested.coerced()
        context.blockerDataStore.edit { prefs ->
            val current = prefs.feedBudget()
            if (budget.isLooserThan(current)) {
                val cooldown = prefs[KEY_COOLDOWN_MINUTES] ?: BlockerSettings.DEFAULT_COOLDOWN_MINUTES
                prefs[KEY_PENDING_FEED_SCREENS] = budget.screens
                prefs[KEY_PENDING_FEED_RESET_MINUTES] = budget.resetMinutes
                prefs[KEY_PENDING_FEED_REQUESTED_AT] = now
                prefs[KEY_PENDING_FEED_EFFECTIVE_AT] = now + TimeUnit.MINUTES.toMillis(cooldown.toLong())
            } else {
                prefs[KEY_FEED_SCREENS] = budget.screens
                prefs[KEY_FEED_RESET_MINUTES] = budget.resetMinutes
                prefs.clearPendingFeedBudget()
            }
            prefs[KEY_INITIALIZED] = true
        }
    }

    /** Drops a loosening that has not landed yet, the way cancelling a pending disable does. */
    suspend fun cancelPendingFeedBudget() {
        context.blockerDataStore.edit { it.clearPendingFeedBudget() }
    }

    /** The cooldown can only be made longer - shortening it would defeat the point. */
    suspend fun setCooldownMinutes(minutes: Int) {
        context.blockerDataStore.edit { prefs ->
            val currentValue = prefs[KEY_COOLDOWN_MINUTES] ?: BlockerSettings.DEFAULT_COOLDOWN_MINUTES
            prefs[KEY_COOLDOWN_MINUTES] = minutes.coerceAtLeast(currentValue).coerceAtMost(MAX_COOLDOWN_MINUTES)
        }
    }

    private fun Preferences.disabledRules(): Set<String> =
        this[KEY_DISABLED_RULES] ?: if (this[KEY_INITIALIZED] == true) {
            emptySet()
        } else {
            RuleCatalog.DEFAULT_DISABLED_IDS
        }

    private fun Preferences.feedBudget(): FeedBudget = FeedBudget(
        screens = this[KEY_FEED_SCREENS] ?: FeedBudget.DEFAULT_FEED_SCREENS,
        resetMinutes = this[KEY_FEED_RESET_MINUTES] ?: FeedBudget.DEFAULT_FEED_RESET_MINUTES,
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.clearPendingFeedBudget() {
        remove(KEY_PENDING_FEED_SCREENS)
        remove(KEY_PENDING_FEED_RESET_MINUTES)
        remove(KEY_PENDING_FEED_REQUESTED_AT)
        remove(KEY_PENDING_FEED_EFFECTIVE_AT)
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.clearPending() {
        remove(KEY_PENDING_TARGET)
        remove(KEY_PENDING_REQUESTED_AT)
        remove(KEY_PENDING_EFFECTIVE_AT)
    }

    private fun Preferences.toSettings(): BlockerSettings {
        val target = this[KEY_PENDING_TARGET]
        val effectiveAt = this[KEY_PENDING_EFFECTIVE_AT]
        return BlockerSettings(
            protectionEnabled = this[KEY_PROTECTION_ENABLED] ?: true,
            disabledRuleIds = disabledRules(),
            pending = if (target != null && effectiveAt != null) {
                PendingDisable(
                    target = target,
                    requestedAt = this[KEY_PENDING_REQUESTED_AT] ?: effectiveAt,
                    effectiveAt = effectiveAt,
                )
            } else {
                null
            },
            cooldownMinutes = this[KEY_COOLDOWN_MINUTES] ?: BlockerSettings.DEFAULT_COOLDOWN_MINUTES,
            feedBudget = feedBudget(),
            pendingFeedBudget = pendingFeedBudget(),
        )
    }

    private fun Preferences.pendingFeedBudget(): PendingFeedBudget? {
        val screens = this[KEY_PENDING_FEED_SCREENS] ?: return null
        val resetMinutes = this[KEY_PENDING_FEED_RESET_MINUTES] ?: return null
        val effectiveAt = this[KEY_PENDING_FEED_EFFECTIVE_AT] ?: return null
        return PendingFeedBudget(
            budget = FeedBudget(screens, resetMinutes),
            requestedAt = this[KEY_PENDING_FEED_REQUESTED_AT] ?: effectiveAt,
            effectiveAt = effectiveAt,
        )
    }

    companion object {
        const val MAX_COOLDOWN_MINUTES = 7 * 24 * 60

        private val KEY_PROTECTION_ENABLED = booleanPreferencesKey("protection_enabled")
        private val KEY_DISABLED_RULES = stringSetPreferencesKey("disabled_rules")
        private val KEY_PENDING_TARGET = stringPreferencesKey("pending_target")
        private val KEY_PENDING_REQUESTED_AT = longPreferencesKey("pending_requested_at")
        private val KEY_PENDING_EFFECTIVE_AT = longPreferencesKey("pending_effective_at")
        private val KEY_COOLDOWN_MINUTES = intPreferencesKey("cooldown_minutes")
        private val KEY_FEED_SCREENS = intPreferencesKey("feed_budget_screens")
        private val KEY_FEED_RESET_MINUTES = intPreferencesKey("feed_budget_reset_minutes")
        private val KEY_PENDING_FEED_SCREENS = intPreferencesKey("pending_feed_budget_screens")
        private val KEY_PENDING_FEED_RESET_MINUTES =
            intPreferencesKey("pending_feed_budget_reset_minutes")
        private val KEY_PENDING_FEED_REQUESTED_AT =
            longPreferencesKey("pending_feed_budget_requested_at")
        private val KEY_PENDING_FEED_EFFECTIVE_AT =
            longPreferencesKey("pending_feed_budget_effective_at")
        private val KEY_INITIALIZED = booleanPreferencesKey("initialized")
    }
}
