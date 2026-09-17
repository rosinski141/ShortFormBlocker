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

    /**
     * What the stateful policies made of this screen. The snapshot alone cannot explain a feed
     * block - the budget is spent over a whole visit - so the debug screen shows this line too.
     */
    @Volatile
    var lastPolicyNote: String? = null
        private set

    fun record(snapshot: ScreenSnapshot, matchedRuleId: String?, policyNote: String? = null) {
        last = snapshot
        lastMatchedRuleId = matchedRuleId
        lastPolicyNote = policyNote
        lastCapturedAt = System.currentTimeMillis()
    }

    fun clear() {
        last = null
        lastMatchedRuleId = null
        lastPolicyNote = null
        lastCapturedAt = 0L
    }
}
