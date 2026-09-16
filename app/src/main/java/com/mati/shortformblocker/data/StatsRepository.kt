package com.mati.shortformblocker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class BlockStats(
    val today: Int = 0,
    val last7Days: Int = 0,
    val allTime: Int = 0,
    /** Blocks per rule id, all time. */
    val perRule: Map<String, Int> = emptyMap(),
    /** Rule id of the most recent block, and the screen that triggered it. */
    val lastBlockRuleId: String? = null,
    val lastBlockDump: String? = null,
)

/**
 * Block counters, stored as two small `key=value|key=value` strings rather than a database - there
 * are at most 30 day buckets and a handful of rules.
 */
class StatsRepository(private val context: Context) {

    val stats: Flow<BlockStats> = context.blockerDataStore.data.map { prefs ->
        val days = parseCounts(prefs[KEY_DAILY])
        val perRule = parseCounts(prefs[KEY_PER_RULE])
        val today = LocalDate.now()
        val weekStart = today.minusDays(6)
        BlockStats(
            today = days[today.toString()] ?: 0,
            last7Days = days.entries.sumOf { (day, count) ->
                if (runCatching { LocalDate.parse(day) >= weekStart }.getOrDefault(false)) count else 0
            },
            allTime = perRule.values.sum(),
            perRule = perRule,
            lastBlockRuleId = prefs[KEY_LAST_BLOCK_RULE],
            lastBlockDump = prefs[KEY_LAST_BLOCK_DUMP],
        )
    }

    /**
     * [evidence] is the dump of the screen that triggered the block. It survives a service restart,
     * which the in-memory snapshot holder does not, and it is the only way to find out *why* a rule
     * fired on a screen you did not expect.
     */
    suspend fun recordBlock(
        ruleId: String,
        evidence: String? = null,
        today: LocalDate = LocalDate.now(),
    ) {
        context.blockerDataStore.edit { prefs ->
            prefs[KEY_LAST_BLOCK_RULE] = ruleId
            if (evidence != null) prefs[KEY_LAST_BLOCK_DUMP] = evidence.take(MAX_EVIDENCE_CHARS)
            val days = parseCounts(prefs[KEY_DAILY]).toMutableMap()
            val key = today.toString()
            days[key] = (days[key] ?: 0) + 1
            prefs[KEY_DAILY] = formatCounts(days.pruneToRecentDays(today))

            val perRule = parseCounts(prefs[KEY_PER_RULE]).toMutableMap()
            perRule[ruleId] = (perRule[ruleId] ?: 0) + 1
            prefs[KEY_PER_RULE] = formatCounts(perRule)
        }
    }

    suspend fun reset() {
        context.blockerDataStore.edit { prefs ->
            prefs.remove(KEY_DAILY)
            prefs.remove(KEY_PER_RULE)
        }
    }

    private fun Map<String, Int>.pruneToRecentDays(today: LocalDate): Map<String, Int> {
        val cutoff = today.minusDays(RETENTION_DAYS)
        return filterKeys { day ->
            runCatching { LocalDate.parse(day) >= cutoff }.getOrDefault(false)
        }
    }

    companion object {
        const val RETENTION_DAYS = 30L
        const val MAX_EVIDENCE_CHARS = 14_000

        private val KEY_DAILY = stringPreferencesKey("daily_block_counts")
        private val KEY_PER_RULE = stringPreferencesKey("per_rule_block_counts")
        private val KEY_LAST_BLOCK_RULE = stringPreferencesKey("last_block_rule")
        private val KEY_LAST_BLOCK_DUMP = stringPreferencesKey("last_block_dump")

        fun parseCounts(raw: String?): Map<String, Int> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split('|').mapNotNull { entry ->
                val parts = entry.split('=')
                val count = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size == 2 && parts[0].isNotBlank() && count != null) parts[0] to count else null
            }.toMap()
        }

        fun formatCounts(counts: Map<String, Int>): String =
            counts.entries.joinToString("|") { "${it.key}=${it.value}" }
    }
}
