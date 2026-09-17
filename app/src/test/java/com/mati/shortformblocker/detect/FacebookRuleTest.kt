package com.mati.shortformblocker.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every snapshot in here was captured from the real app (2026-09-17, com.facebook.katana on
 * Android 16), which is why the labels read the way they do.
 *
 * The case that matters is the full-screen player: a reel opened from the feed, a profile or a
 * share plays on its own screen with no bottom nav, so the rule's only working signal - the
 * selected Reels tab - was missing and Facebook Reels went unblocked there. Note what is not in
 * these trees: a usable view id. Facebook strips its resource names, so every id it reports is
 * `com.facebook.katana:id/(name removed)`.
 */
class FacebookRuleTest {

    private val allRules = RuleCatalog.ALL

    @Test
    fun `full screen reel player matches although nothing is selected`() {
        val labels = setOf(
            "Reel details",
            "Navigate to your Reels profile",
            "Tap to show video controls",
            "Follow Azeroth Nostalgia",
            "Add a Comment",
            "Back",
        )
        val snapshot = ScreenSnapshot(
            packageName = "com.facebook.katana",
            viewIds = setOf("android:id/content", "com.facebook.katana:id/(name removed)"),
            visibleViewIds = setOf("android:id/content", "com.facebook.katana:id/(name removed)"),
            contentDescriptions = labels,
            visibleContentDescriptions = labels,
            texts = setOf("UP NEXT:", "0:05", "5 comments"),
            visibleTexts = setOf("UP NEXT:", "0:05", "5 comments"),
        )
        assertEquals(RuleCatalog.FACEBOOK_REELS, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `reels tab matches`() {
        val labels = setOf(
            "Reels tab details",
            "Navigate to your Reels profile",
            "Home, tab 1 of 6",
            "Reels, tab 2 of 6",
        )
        val snapshot = ScreenSnapshot(
            packageName = "com.facebook.katana",
            viewIds = setOf("com.facebook.katana:id/(name removed)"),
            visibleViewIds = setOf("com.facebook.katana:id/(name removed)"),
            contentDescriptions = labels,
            visibleContentDescriptions = labels,
            selectedLabels = setOf("Reels, tab 2 of 6"),
            visibleSelectedLabels = setOf("Reels, tab 2 of 6"),
        )
        assertEquals(RuleCatalog.FACEBOOK_REELS, RuleMatcher.match(snapshot, allRules))
    }

    /**
     * The home feed plays reels inline and labels them `Reel`, and it carries the Reels tab button
     * on every screen. Matching either would bounce the user out of the feed they were reading -
     * the Facebook version of the Instagram home-feed bug.
     */
    @Test
    fun `home feed is the budgeted feed rule, never the reels rule`() {
        val labels = setOf(
            "Reel",
            "Mute sound",
            "Story tray",
            "Create, double-tap to create a new post, story or reel",
            "Home, tab 1 of 6",
            "Reels, tab 2 of 6",
            "Marketplace, tab 4 of 6",
        )
        val snapshot = ScreenSnapshot(
            packageName = "com.facebook.katana",
            viewIds = setOf("android:id/list", "com.facebook.katana:id/(name removed)"),
            visibleViewIds = setOf("android:id/list", "com.facebook.katana:id/(name removed)"),
            contentDescriptions = labels,
            visibleContentDescriptions = labels,
            texts = setOf("Create story", "Follow"),
            visibleTexts = setOf("Create story", "Follow"),
            selectedLabels = setOf("Home, tab 1 of 6"),
            visibleSelectedLabels = setOf("Home, tab 1 of 6"),
        )
        // The feed rule claims this screen, which only blocks once the scroll budget is spent -
        // what must never happen is the reels rule firing here and bouncing you out on arrival.
        assertEquals(RuleCatalog.FACEBOOK_FEED, RuleMatcher.match(snapshot, allRules))
    }

    @Test
    fun `marketplace does not match`() {
        val labels = setOf("Marketplace, tab 4 of 6", "Reels, tab 2 of 6", "Search")
        val snapshot = ScreenSnapshot(
            packageName = "com.facebook.katana",
            contentDescriptions = labels,
            visibleContentDescriptions = labels,
            selectedLabels = setOf("Marketplace, tab 4 of 6"),
            visibleSelectedLabels = setOf("Marketplace, tab 4 of 6"),
        )
        assertNull(RuleMatcher.match(snapshot, allRules))
    }

    /** A player label from a screen no longer in front of the user is not a reason to act. */
    @Test
    fun `a reel player label that is not on screen leaves this the feed, not the player`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.facebook.katana",
            contentDescriptions = setOf("Reel details", "Navigate to your Reels profile"),
            visibleContentDescriptions = setOf("Home, tab 1 of 6"),
            selectedLabels = setOf("Home, tab 1 of 6"),
            visibleSelectedLabels = setOf("Home, tab 1 of 6"),
        )
        assertEquals(RuleCatalog.FACEBOOK_FEED, RuleMatcher.match(snapshot, allRules))
    }

    /** A profile's Reels grid is a wall of reels waiting to be opened, so it counts as the surface. */
    @Test
    fun `a profile reels grid matches`() {
        val labels = setOf("Reel number 1,2.6 thousand plays", "Reels, 3 of 4", "Photos, 2 of 4")
        val snapshot = ScreenSnapshot(
            packageName = "com.facebook.katana",
            contentDescriptions = labels,
            visibleContentDescriptions = labels,
            selectedLabels = setOf("Reels, 3 of 4"),
            visibleSelectedLabels = setOf("Reels, 3 of 4"),
        )
        assertEquals(RuleCatalog.FACEBOOK_REELS, RuleMatcher.match(snapshot, allRules))
    }

    /**
     * The ids Facebook hands out carry no information at all, so the rule must not lean on them -
     * this is the guard for someone adding view id signals back because every other rule has them.
     */
    @Test
    fun `the facebook rule leans on no view ids`() {
        assertEquals(emptyList<String>(), RuleCatalog.FACEBOOK_REELS.viewIdContains)
    }

    @Test
    fun `another app carrying the same labels is never matched`() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.apps.messaging",
            contentDescriptions = setOf("Reel details"),
            visibleContentDescriptions = setOf("Reel details"),
        )
        assertNull(RuleMatcher.match(snapshot, allRules))
    }
}
