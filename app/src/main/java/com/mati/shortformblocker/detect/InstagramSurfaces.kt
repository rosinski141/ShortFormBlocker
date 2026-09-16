package com.mati.shortformblocker.detect

/**
 * Instagram-specific screen reading, kept in one place because it is the part most likely to need
 * updating after an Instagram redesign.
 *
 * Verified on the real app (2026-09-16): the Reels viewer labels the reel in front of you
 * `Reel by <author>. Double-tap to play or pause.`, which is what lets us tell "still the reel your
 * friend sent" from "you have scrolled into the feed".
 */
object InstagramSurfaces {

    const val PACKAGE = "com.instagram.android"

    /** Every Instagram build the rules target. */
    val PACKAGES = setOf(PACKAGE, "com.instagram.lite")

    private const val REEL_BY_PREFIX = "reel by "

    /** The bottom-nav Reels button. Present whenever the main tab host is on screen. */
    private const val CLIPS_TAB_ID = "clips_tab"

    /**
     * The bottom-nav Home button, used to bounce out of Reels without throwing the user out of
     * Instagram altogether. Several fragments because the id differs between builds; the first one
     * that resolves wins, and if none do we fall back to the device home screen.
     */
    val HOME_TAB_ID_FRAGMENTS = listOf("feed_tab", "home_tab", "tab_feed")

    /**
     * Direct-message screens. A reel only earns a pass if you got to it from one of these.
     *
     * These fragments are matched against view ids only - no message text is ever read or stored.
     * The list is deliberately broad: if none of them match, the feature fails closed and reels are
     * blocked as before.
     */
    // Thread screens only. `direct_tab` is deliberately absent: like `clips_tab` it is a bottom-nav
    // button present on every screen of the app, so trusting it hands a pass to the whole feed.
    val DM_SCREEN_ID_FRAGMENTS = listOf(
        "thread_message_list",
        "direct_thread",
        "thread_composer",
        "row_thread",
        "message_list",
    )

    /** True when the user is browsing the Reels tab itself, which never earns a pass. */
    fun isReelsTabSurface(snapshot: ScreenSnapshot): Boolean =
        snapshot.containsViewIdFragment(CLIPS_TAB_ID) && snapshot.hasVisibleSelectedLabel("Reels")

    fun isDirectMessageScreen(snapshot: ScreenSnapshot): Boolean =
        DM_SCREEN_ID_FRAGMENTS.any(snapshot::containsViewIdFragment)

    /** True for a scroll event coming from the reels pager rather than, say, a comments sheet. */
    fun isClipsScrollSource(viewId: String?): Boolean =
        viewId == null || viewId.contains("clips", ignoreCase = true)

    /** Authors of the reels actually on screen right now. */
    fun visibleReelAuthors(snapshot: ScreenSnapshot): Set<String> =
        snapshot.visibleContentDescriptions.mapNotNull(::reelAuthor).toSet()

    /** `Reel by wolfeyemedia. Double-tap to play or pause.` -> `wolfeyemedia` */
    fun reelAuthor(description: String): String? {
        if (!description.startsWith(REEL_BY_PREFIX, ignoreCase = true)) return null
        return description.drop(REEL_BY_PREFIX.length)
            .substringBefore('.')
            .trim()
            .takeIf { it.isNotEmpty() }
    }
}
