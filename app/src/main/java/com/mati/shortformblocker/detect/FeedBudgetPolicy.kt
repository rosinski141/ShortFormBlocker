package com.mati.shortformblocker.detect

/**
 * "The first screen of the feed is fine. The fortieth is the one you regret."
 *
 * A home feed cannot be blocked the way a Reels tab can: it is the front door of the app, and
 * throwing someone out of it takes their messages, events and notifications with it. So the feed is
 * not blocked for *being* the feed, it is blocked for *going on too long* - you get a budget of
 * screens per visit, and when it runs out the feed closes until you have been out of the app for a
 * while. Coming back from the launcher five seconds later does not buy a new budget.
 *
 * Two things this has to survive, both seen on the real apps:
 *
 * - **The feed stops looking like the feed.** Facebook hides its bottom nav the moment you scroll
 *   and leaves it hidden, so on a scrolled feed nothing is *visible* that says "this is the feed" -
 *   the Home tab is still in the tree, still marked selected, just not on screen. Which tab is open
 *   is therefore read from the whole tree rather than from what is visible, and on top of that the
 *   feed is *latched*: it stays identified until a screen shows up that is clearly somewhere else -
 *   another tab is open, or Instagram has a DM thread up. A screen we cannot place at all is
 *   treated as still the feed, because that is what it is nine times out of ten: a post you opened
 *   from it, on the way back to it.
 * - **Landing on the feed is not scrolling it.** Blocking follows the *scroll*, not the state: an
 *   overspent feed you open, glance at and leave is never blocked, so the app still works for the
 *   messages and notifications you came for. Scroll it again and it closes again, immediately -
 *   there is no fresh budget, only a fresh refusal.
 *
 * Pure state machine: the service feeds it snapshots and scroll distances, and it answers one
 * question. One budget per app, so Facebook and Instagram cannot spend each other's.
 */
class FeedBudgetPolicy(
    private val screenHeightPx: Int,
    private val budget: () -> FeedBudget,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private class Visit {
        /** Latched: are we in this app's feed, as far as we can tell? */
        var inFeed = false

        var scrolledPx = 0L
        var lastSeenAt = 0L

        /**
         * When the budget was last spent *on a scroll*. Blocking keys off this rather than a flag,
         * so that putting the phone down, or leaving for another app, ends the block: an overspent
         * feed you merely walk past is not the thing being stopped, scrolling on is.
         */
        var armedAt = 0L

        fun clear() {
            inFeed = false
            scrolledPx = 0L
            armedAt = 0L
        }
    }

    private val visits = HashMap<String, Visit>()

    /**
     * @param isFeedScreen whether a [BlockMode.BUDGETED_FEED] rule matched this screen.
     * @return true when the feed should be blocked now.
     */
    fun onSnapshot(snapshot: ScreenSnapshot, isFeedScreen: Boolean): Boolean {
        val now = clock()
        val visit = visits.getOrPut(snapshot.packageName) { Visit() }
        if (visit.lastSeenAt != 0L && now - visit.lastSeenAt > resetAfterMs()) visit.clear()
        visit.lastSeenAt = now

        if (isFeedScreen) {
            visit.inFeed = true
        } else if (leftTheFeed(snapshot)) {
            visit.inFeed = false
            visit.armedAt = 0L
        }

        return visit.inFeed && isArmed(visit, now)
    }

    /** Scroll distance in pixels, from the accessibility event. Only feed scrolling spends budget. */
    fun onScroll(packageName: String, distancePx: Int) {
        val visit = visits[packageName] ?: return
        if (!visit.inFeed) return
        visit.scrolledPx += distancePx
        if (visit.scrolledPx >= budgetPx()) visit.armedAt = clock()
    }

    fun reset() = visits.clear()

    /** One line for the block evidence, so an unexpected block can be explained afterwards. */
    fun describe(packageName: String): String {
        val visit = visits[packageName] ?: return "no feed visit recorded"
        val screens = visit.scrolledPx.toDouble() / screenHeightPx.coerceAtLeast(1)
        val spent = String.format("%.1f", screens)
        return "${spent} of ${budget().screens} screens scrolled this visit" +
            (if (visit.scrolledPx >= budgetPx()) ", budget spent" else "") +
            (if (isArmed(visit, clock())) ", scrolling on" else "") +
            (if (visit.inFeed) "" else ", not in the feed")
    }

    /**
     * A screen that is definitely somewhere else: another tab is open, or Instagram has a DM thread
     * on screen. Anything we cannot place - a post, a comment sheet, a photo - leaves the latch
     * alone, because those are reached from the feed and scrolling them is the same sitting.
     */
    private fun leftTheFeed(snapshot: ScreenSnapshot): Boolean {
        if (InstagramSurfaces.isDirectMessageScreen(snapshot)) return true
        // The whole tree, not just what is on screen, for the same reason the rule matches that way.
        val selected = snapshot.selectedLabels
        return selected.isNotEmpty() &&
            selected.none { it.contains(InstagramSurfaces.HOME_TAB_LABEL, ignoreCase = true) }
    }

    /** Over budget and scrolling right now, rather than merely over budget. */
    private fun isArmed(visit: Visit, now: Long): Boolean =
        visit.armedAt != 0L && now - visit.armedAt <= ARM_WINDOW_MS

    private fun budgetPx(): Long = screenHeightPx.toLong() * budget().screens

    private fun resetAfterMs(): Long = budget().resetMinutes * 60_000L

    companion object {
        /**
         * How long an over-budget scroll keeps blocking. Long enough for the block card and the two
         * Back presses behind it, short enough that coming back to the app later - or just sitting
         * still - lets you walk past the feed to your messages without being thrown out.
         */
        const val ARM_WINDOW_MS = 2_500L
    }
}
