package com.mati.shortformblocker.detect

/**
 * Keeps the most recent snapshot in memory so the debug screen can show it.
 *
 * Nothing here is written to disk: open Shorts, switch to this app, read the view ids, update
 * [RuleCatalog]. Killing the service clears it.
 */
object SnapshotHolder {

    @Volatile
    var last: ScreenSnapshot? = null
        private set

    @Volatile
    var lastMatchedRuleId: String? = null
        private set

    @Volatile
    var lastCapturedAt: Long = 0L
        private set

    fun record(snapshot: ScreenSnapshot, matchedRuleId: String?) {
        last = snapshot
        lastMatchedRuleId = matchedRuleId
        lastCapturedAt = System.currentTimeMillis()
    }

    fun clear() {
        last = null
        lastMatchedRuleId = null
        lastCapturedAt = 0L
    }
}
