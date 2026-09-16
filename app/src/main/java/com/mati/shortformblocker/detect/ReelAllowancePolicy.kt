package com.mati.shortformblocker.detect

/**
 * "You can watch what a friend sent you, but you cannot scroll on from it."
 *
 * A reel earns a pass only when it was opened from a direct-message screen. The pass covers the
 * reel that was opened and nothing after it: the moment the pager scrolls, or the reel in front of
 * you is no longer the one the pass was granted for, blocking resumes for the rest of that viewer
 * session. Going back to the conversation and opening another reel earns a fresh pass, so there is
 * no limit on reels friends actually send - only on the feed behind them.
 *
 * Pure state machine: the service feeds it snapshots and scroll events, and it answers one question.
 */
class ReelAllowancePolicy(private val clock: () -> Long = System::currentTimeMillis) {

    private data class Pass(val authors: Set<String>, val grantedAt: Long)

    private var cameFromDirectMessages = false
    private var pass: Pass? = null

    /** Once the pass is spent, no new pass until the viewer is left - no swiping back and forth. */
    private var spent = false

    /**
     * @param isReelScreen whether the reels rule matched this screen.
     * @return true if this reel should be allowed through despite the rule matching.
     */
    fun onSnapshot(snapshot: ScreenSnapshot, isReelScreen: Boolean): Boolean {
        if (snapshot.packageName != InstagramSurfaces.PACKAGE) {
            reset()
            return false
        }

        if (!isReelScreen) {
            // Any non-reel Instagram screen ends the viewer session and re-arms the pass.
            cameFromDirectMessages = InstagramSurfaces.isDirectMessageScreen(snapshot)
            pass = null
            spent = false
            return false
        }

        if (InstagramSurfaces.isReelsTabSurface(snapshot)) return false
        if (spent) return false

        val current = pass
        if (current == null) {
            if (!cameFromDirectMessages) return false
            pass = Pass(InstagramSurfaces.visibleReelAuthors(snapshot), clock())
            return true
        }

        // Mid-swipe both reels are briefly visible, so the pass only ends once the original reel
        // has actually left the screen.
        if (current.authors.isNotEmpty()) {
            val visible = InstagramSurfaces.visibleReelAuthors(snapshot)
            if (visible.isNotEmpty() && current.authors.none { it in visible }) {
                spend()
                return false
            }
        }
        return true
    }

    /**
     * A scroll inside the reels pager means the next reel, which nobody sent you. The grace period
     * ignores the settling scroll the pager emits as the viewer opens.
     */
    fun onClipsScroll() {
        val current = pass ?: return
        if (clock() - current.grantedAt < SCROLL_GRACE_MS) return
        spend()
    }

    fun reset() {
        cameFromDirectMessages = false
        pass = null
        spent = false
    }

    /** One line for the debug screen. */
    fun describe(): String = when {
        pass != null -> "pass active for ${pass?.authors?.joinToString().orEmpty().ifEmpty { "this reel" }}"
        spent -> "pass spent - scrolled past the shared reel"
        cameFromDirectMessages -> "came from DMs, a reel opened now would be allowed"
        else -> "no pass"
    }

    private fun spend() {
        pass = null
        spent = true
    }

    companion object {
        /** Ignore pager scrolls this soon after a pass is granted; the viewer settling emits one. */
        const val SCROLL_GRACE_MS = 700L
    }
}
