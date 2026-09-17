package com.mati.shortformblocker.detect

/**
 * How much feed you get per visit, and how long you have to stay away to earn a fresh one.
 *
 * Measured in screens rather than swipes or seconds: a fling and a slow drag are wildly different
 * numbers of scroll events but the same amount of feed going past, and seconds reward putting the
 * phone down mid-feed rather than leaving.
 */
data class FeedBudget(
    val screens: Int = DEFAULT_FEED_SCREENS,
    val resetMinutes: Int = DEFAULT_FEED_RESET_MINUTES,
) {
    /** True when this budget allows more scrolling than [other] - the direction that has to wait. */
    fun isLooserThan(other: FeedBudget): Boolean =
        screens > other.screens || resetMinutes < other.resetMinutes

    fun coerced(): FeedBudget = FeedBudget(
        screens = screens.coerceIn(MIN_FEED_SCREENS, MAX_FEED_SCREENS),
        resetMinutes = resetMinutes.coerceIn(MIN_FEED_RESET_MINUTES, MAX_FEED_RESET_MINUTES),
    )

    companion object {
        const val DEFAULT_FEED_SCREENS = 12
        const val DEFAULT_FEED_RESET_MINUTES = 15
        const val MIN_FEED_SCREENS = 1
        const val MAX_FEED_SCREENS = 100
        const val MIN_FEED_RESET_MINUTES = 1
        const val MAX_FEED_RESET_MINUTES = 12 * 60
    }
}
