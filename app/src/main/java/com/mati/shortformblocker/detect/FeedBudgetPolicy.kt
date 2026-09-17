package com.mati.shortformblocker.detect

/**
 * What a feed visit looks like from the outside, so the UI can show a lockout countdown.
 *
 * Absolute timestamps rather than durations: the reader is a once-a-second recomposition, not
 * something that has been counting, and [refillsAt] stays right however late it is read.
 */
data class FeedVisitState(
    val packageName: String,
    val screensScrolled: Double,
    val screensAllowed: Int,
    /** The budget for this visit has been scrolled through. */
    val spent: Boolean,
    /**
     * When a fresh budget is earned: the reset window measured from the last scroll of the feed.
     * It keeps running while you are in the app doing something else, and only scrolling the feed
     * again pushes it back.
     */
    val refillsAt: Long,
) {
    fun refillRemainingMillis(now: Long): Long = (refillsAt - now).coerceAtLeast(0L)

    /** Past its reset window, so the next screen of this app starts a fresh visit. */
    fun isStale(now: Long): Boolean = now >= refillsAt

    /** Locked out right now: the budget is gone and the visit has not timed out yet. */
    fun isLockedOut(now: Long): Boolean = spent && !isStale(now)
}

/**
 * "The first screen of the feed is fine. The fortieth is the one you regret."
 *
 * A home feed cannot be blocked the way a Reels tab can: it is the front door of the app, and
 * throwing someone out of it takes their messages, events and notifications with it. So the feed is
 * not blocked for *being* the feed, it is blocked for *going on too long* - you get a budget of
 * screens per visit, and when it runs out the feed closes until you have left the feed alone for a
 * while. Coming back from the launcher five seconds later does not buy a new budget, but reading
 * your messages in the same app does not hold the refill up either.
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

        /**
         * When the feed was last *scrolled*. The reset window runs from here rather than from the
         * last screen of the app: measuring it from the app meant that answering a DM at minute
         * nineteen pushed the refill back another twenty, so anyone who checks their messages often
         * enough never got the feed back at all. What is rationed is feed scrolling, so that is
         * what the wait is measured from.
         */
        var lastScrolledAt = 0L

        /**
         * When the budget was last spent *on a scroll*. Blocking keys off this rather than a flag,
         * so that putting the phone down, or leaving for another app, ends the block: an overspent
         * feed you merely walk past is not the thing being stopped, scrolling on is.
         */
        var armedAt = 0L

        fun clear() {
            inFeed = false
            scrolledPx = 0L
            lastScrolledAt = 0L
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
        if (visit.lastScrolledAt != 0L && now - visit.lastScrolledAt > resetAfterMs()) visit.clear()

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
        val now = clock()
        visit.scrolledPx += distancePx
        visit.lastScrolledAt = now
        if (visit.scrolledPx >= budgetPx()) visit.armedAt = now
    }

    fun reset() = visits.clear()

    /**
     * A read-only view of the visits in progress, for the home screen's lockout countdown. Handed
     * out as a copy: this map is mutated from accessibility callbacks and read from the UI.
     */
    fun states(): Map<String, FeedVisitState> {
        val allowed = budget().screens
        val resetAfter = resetAfterMs()
        val spentAt = budgetPx()
        return visits.mapValues { (packageName, visit) ->
            FeedVisitState(
                packageName = packageName,
                screensScrolled = screensScrolled(visit),
                screensAllowed = allowed,
                spent = visit.scrolledPx >= spentAt,
                refillsAt = visit.lastScrolledAt + resetAfter,
            )
        }
    }

    /** One line for the block evidence, so an unexpected block can be explained afterwards. */
    fun describe(packageName: String): String {
        val visit = visits[packageName] ?: return "no feed visit recorded"
        val spent = String.format("%.1f", screensScrolled(visit))
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

    private fun screensScrolled(visit: Visit): Double =
        visit.scrolledPx.toDouble() / screenHeightPx.coerceAtLeast(1)

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
