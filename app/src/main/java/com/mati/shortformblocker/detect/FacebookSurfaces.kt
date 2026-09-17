package com.mati.shortformblocker.detect

/**
 * Facebook-specific screen reading, kept in one place because Facebook gives a blocker less to work
 * with than any other app in the catalog.
 *
 * Captured from the real app (2026-09-17, com.facebook.katana on Android 16):
 *
 * - **There are no view ids.** Facebook ships with its resource names stripped, so every id in the
 *   tree comes back as the literal string `com.facebook.katana:id/(name removed)`. The view-id
 *   signals every other rule leans on can never fire here, which is why this file exists.
 * - **Only the bottom nav reports a selection.** On the Reels tab the selected label is
 *   `Reels, tab 2 of 6`, which is a good signal - but a reel opened from the feed, a profile, a
 *   share or a notification plays on its own screen with no bottom nav at all, so nothing on it is
 *   selected. That screen was the hole: it matched nothing and scrolled on forever.
 *
 * What is left is labels. Facebook wraps a playing reel in a container whose content description
 * names the surface, and those are specific enough to act on - unlike the bare `Reel` description
 * on an inline feed video, which is on the home feed itself and must never be matched.
 */
object FacebookSurfaces {

    const val PACKAGE = "com.facebook.katana"

    /** Every Facebook build the rules target. */
    val PACKAGES = setOf(PACKAGE, "com.facebook.lite")

    /** The bottom-nav Reels button, `Reels, tab 2 of 6`, matched only when it is the open tab. */
    const val REELS_TAB_LABEL = "Reels"

    /**
     * The bottom-nav Home button, `Home, tab 1 of 6`, which is what identifies the feed.
     *
     * Facebook hides the whole bottom nav while you scroll the feed, so this signal blinks out
     * mid-fling - that is why [FeedBudgetPolicy] latches the surface instead of reading it fresh
     * from every snapshot.
     */
    const val HOME_TAB_LABEL = "Home"

    /**
     * Containers that only exist while a reel is playing full screen.
     *
     * `Reel details` is the full-screen player reached from anywhere outside the Reels tab, and
     * `Reels tab details` is the tab's own player - they are separate strings rather than one
     * shared fragment because neither contains the other. `Navigate to your Reels profile` is the
     * header button both of them carry, kept as a third signal for the same reason the other rules
     * list several ids: any one of them being renamed should not switch blocking off.
     *
     * Deliberately absent: `Reel`, the description of a reel playing inline in the home feed, and
     * `Reels tab details` aside, anything that is merely *about* reels. Matching those would fire
     * the rule on the home feed and the escalation would throw the user out of Facebook.
     */
    val REELS_PLAYER_LABELS = listOf(
        "Reel details",
        "Reels tab details",
        "Navigate to your Reels profile",
    )
}
