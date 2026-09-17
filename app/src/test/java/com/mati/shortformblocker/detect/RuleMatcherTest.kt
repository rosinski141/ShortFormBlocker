package com.mati.shortformblocker.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Both directions matter, and the negative cases matter more: a rule that fails to fire costs you a
 * few minutes of Shorts, while a rule that fires on a normal screen makes the whole app unusable.
 */
class RuleMatcherTest {

    private val allRules = RuleCatalog.ALL

    // ---- YouTube ------------------------------------------------------------------------------

    @Test
    fun `youtube shorts player matches`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = setOf(
                "com.google.android.youtube:id/reel_recycler",
                "com.google.android.youtube:id/reel_progress_bar",
            ),
        )
        assertEquals(RuleCatalog.YOUTUBE_SHORTS, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `youtube shorts tab selected matches`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            contentDescriptions = setOf("Home", "Shorts", "Subscriptions"),
            selectedLabels = setOf("Shorts, tab 2 of 5"),
        )
        assertEquals(RuleCatalog.YOUTUBE_SHORTS, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `youtube home feed does not match even though a Shorts tab exists`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = setOf(
                "com.google.android.youtube:id/results",
                "com.google.android.youtube:id/pivot_bar",
            ),
            texts = setOf("Shorts", "Subscriptions"),
            contentDescriptions = setOf("Shorts"),
            selectedLabels = setOf("Home, tab 1 of 5"),
        )
        assertNull(RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `youtube watch page does not match`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            viewIds = setOf(
                "com.google.android.youtube:id/watch_player",
                "com.google.android.youtube:id/player_control_play_pause_replay_button",
            ),
            selectedLabels = setOf("Home"),
        )
        assertNull(RuleMatcher.match(snapshot, allRules))
    }

    // ---- Instagram ----------------------------------------------------------------------------

    @Test
    fun `instagram clips viewer matches`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
            visibleViewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
        )
        assertEquals(RuleCatalog.INSTAGRAM_REELS, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `instagram reels tab selected matches`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.instagram.android",
            selectedLabels = setOf("Reels"),
            visibleSelectedLabels = setOf("Reels"),
        )
        assertEquals(RuleCatalog.INSTAGRAM_REELS, RuleMatcher.match(snapshot, allRules))
    }

    /**
     * Captured from the real app (2026-09-16): on the home feed Instagram keeps the Reels page of
     * its swipeable pager attached and selected, off screen. Matching those ids fired the rule on
     * the home feed, so the second Back press of the escalation threw the user out of Instagram
     * altogether - the bug this test exists to keep fixed.
     */
    @Test
    fun `instagram home feed is the budgeted feed although the reels pager is attached off screen`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf(
                "com.instagram.android:id/main_feed_action_bar",
                "com.instagram.android:id/feed_tab",
                "com.instagram.android:id/clips_tab",
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/clips_swipe_refresh_container",
                "com.instagram.android:id/root_clips_layout",
            ),
            visibleViewIds = setOf(
                "com.instagram.android:id/main_feed_action_bar",
                "com.instagram.android:id/feed_tab",
                "com.instagram.android:id/clips_tab",
            ),
            contentDescriptions = setOf("Instagram Home feed", "Reels", "Home"),
            selectedLabels = setOf("Home", "Reels"),
            visibleSelectedLabels = setOf("Home"),
        )
        // The home feed belongs to the budgeted feed rule, which waits for the scroll budget to run
        // out. The reels rule firing here is the bug, and it stays fixed.
        assertEquals(RuleCatalog.INSTAGRAM_FEED, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `instagram feed is the budgeted feed although the Reels tab button is on screen`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/feed_container", "com.instagram.android:id/tab_bar"),
            visibleViewIds = setOf(
                "com.instagram.android:id/feed_container",
                "com.instagram.android:id/tab_bar",
            ),
            contentDescriptions = setOf("Reels", "Home", "Profile"),
            selectedLabels = setOf("Home"),
            visibleSelectedLabels = setOf("Home"),
        )
        assertEquals(RuleCatalog.INSTAGRAM_FEED, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `instagram direct messages do not match`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/thread_message_list"),
            texts = setOf("sent a reel"),
        )
        assertNull(RuleMatcher.match(snapshot, allRules))
    }

    // ---- TikTok -------------------------------------------------------------------------------

    @Test
    fun `tiktok matches on package alone`() {
        val snapshot = ScreenSnapshot(packageName = "com.zhiliaoapp.musically")
        assertEquals(RuleCatalog.TIKTOK, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `tiktok clone package matches`() {
        val snapshot = ScreenSnapshot(packageName = "com.ss.android.ugc.trill")
        assertEquals(RuleCatalog.TIKTOK, RuleMatcher.match(snapshot, allRules))
    }

    // ---- Browsers -----------------------------------------------------------------------------

    @Test
    fun `chrome on a shorts url matches`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.android.chrome",
            viewIds = setOf("com.android.chrome:id/url_bar"),
            urlBarText = "youtube.com/shorts/dqw4w9wgxcq",
        )
        assertEquals(RuleCatalog.BROWSER_SHORT_FORM, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `firefox on tiktok matches`() {
        val snapshot = ScreenSnapshot(
            packageName = "org.mozilla.firefox",
            urlBarText = "https://www.tiktok.com/foryou",
        )
        assertEquals(RuleCatalog.BROWSER_SHORT_FORM, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `chrome on a normal video does not match`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.android.chrome",
            urlBarText = "youtube.com/watch?v=dqw4w9wgxcq",
        )
        assertNull(RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `searching for the word shorts does not match`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.android.chrome",
            texts = setOf("shorts", "running shorts"),
            urlBarText = "google.com/search?q=running+shorts",
        )
        assertNull(RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `chrome with no url bar captured does not match`() {
        val snapshot = ScreenSnapshot(packageName = "com.android.chrome", urlBarText = null)
        assertNull(RuleMatcher.match(snapshot, allRules))
    }

    // ---- Rule wiring --------------------------------------------------------------------------

    @Test
    fun `disabled rules never fire`() {
        val snapshot = ScreenSnapshot(packageName = "com.zhiliaoapp.musically")
        val withoutTikTok = allRules.filterNot { it.id == RuleCatalog.TIKTOK.id }
        assertNull(RuleMatcher.match(snapshot, withoutTikTok))
    }

    @Test
    fun `an unrelated app is never matched`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.apps.messaging",
            viewIds = setOf("com.google.android.apps.messaging:id/reel_recycler"),
            selectedLabels = setOf("Reels", "Shorts"),
            urlBarText = "tiktok.com",
        )
        assertNull(RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `off by default rules are the risky ones only`() {
        assertEquals(
            setOf(RuleCatalog.REDDIT_VIDEO.id, RuleCatalog.X_VIDEO.id),
            RuleCatalog.DEFAULT_DISABLED_IDS,
        )
    }

    @Test
    fun `every rule has at least one signal unless it blocks a whole app`() {
        RuleCatalog.ALL.forEach { rule ->
            val hasSignal = rule.viewIdContains.isNotEmpty() ||
                rule.selectedLabelContains.isNotEmpty() ||
                rule.visibleLabelContains.isNotEmpty() ||
                rule.urlContains.isNotEmpty() ||
                rule.urlHostEquals.isNotEmpty()
            assertTrue(
                "${rule.id} would match on package name alone",
                rule.mode == BlockMode.WHOLE_APP || hasSignal,
            )
        }
    }

    @Test
    fun `rule ids are unique`() {
        assertEquals(RuleCatalog.ALL.size, RuleCatalog.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `empty screen never matches`() {
        assertFalse(RuleCatalog.YOUTUBE_SHORTS.matches(ScreenSnapshot(packageName = "com.google.android.youtube")))
    }
}
