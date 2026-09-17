package com.mati.shortformblocker.detect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The feed budget is the one rule in the app that is allowed to let you through - twelve screens of
 * feed is the point of it - so the tests that matter are the ones about *when* it stops letting you
 * through, and the ones about it not stopping you anywhere else.
 */
class FeedBudgetPolicyTest {

    private val screenHeight = 2000
    private var now = 1_000_000L
    private var budget = FeedBudget(screens = 3, resetMinutes = 15)

    private val policy = FeedBudgetPolicy(
        screenHeightPx = screenHeight,
        budget = { budget },
        clock = { now },
    )

    private val facebookFeed = ScreenSnapshot(
        packageName = "com.facebook.katana",
        selectedLabels = setOf("Home, tab 1 of 6"),
        visibleSelectedLabels = setOf("Home, tab 1 of 6"),
    )

    /**
     * A scrolled Facebook feed, captured from the real app: the bottom nav is hidden, so the Home
     * tab is selected in the tree with nothing visible to show for it.
     */
    private val facebookScrolledFeed = ScreenSnapshot(
        packageName = "com.facebook.katana",
        selectedLabels = setOf("Home, tab 1 of 6"),
    )

    /** A screen with no selection at all - a photo viewer, a comment sheet. */
    private val facebookUnplaceable = ScreenSnapshot(packageName = "com.facebook.katana")

    private val facebookMarketplace = ScreenSnapshot(
        packageName = "com.facebook.katana",
        selectedLabels = setOf("Marketplace, tab 4 of 6"),
        visibleSelectedLabels = setOf("Marketplace, tab 4 of 6"),
    )

    private val instagramFeed = ScreenSnapshot(
        packageName = "com.instagram.android",
        selectedLabels = setOf("Home"),
        visibleSelectedLabels = setOf("Home"),
    )

    private val instagramDirectMessages = ScreenSnapshot(
        packageName = "com.instagram.android",
        viewIds = setOf("com.instagram.android:id/thread_message_list"),
    )

    @Test
    fun `a few screens of feed are fine`() {
        assertFalse(policy.onSnapshot(facebookFeed, isFeedScreen = true))
        scroll(facebookFeed, screens = 2)
        assertFalse(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    @Test
    fun `the feed closes once the budget is spent`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 3)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    /**
     * The one that nearly sank this: Facebook hides its bottom nav the moment you scroll the feed
     * and leaves it hidden, so after the first swipe nothing identifies the screen at all. Measured
     * on the real app - five swipes, not one tab button in the tree. If an unidentified screen did
     * not count as the feed, a hard scroller would never spend a single screen of budget.
     */
    @Test
    fun `scrolling still counts once facebook has hidden its bottom nav`() {
        repeat(3) {
            policy.onSnapshot(facebookScrolledFeed, isFeedScreen = true)
            scroll(facebookScrolledFeed, screens = 1)
        }
        assertTrue(policy.onSnapshot(facebookScrolledFeed, isFeedScreen = true))
    }

    @Test
    fun `a screen with no selection at all is still the feed`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        repeat(3) {
            policy.onSnapshot(facebookUnplaceable, isFeedScreen = false)
            scroll(facebookUnplaceable, screens = 1)
        }
        assertTrue(policy.onSnapshot(facebookUnplaceable, isFeedScreen = false))
    }

    @Test
    fun `the latch only drops for a screen that is clearly somewhere else`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        policy.onSnapshot(facebookMarketplace, isFeedScreen = false)
        scroll(facebookUnplaceable, screens = 5)
        assertFalse(policy.onSnapshot(facebookUnplaceable, isFeedScreen = false))
    }

    /**
     * The whole point of budgeting the feed rather than blocking it: with the budget spent you can
     * still walk past the feed to your messages. It is scrolling on that gets stopped.
     */
    @Test
    fun `an overspent feed blocks on the next scroll, not on arrival`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 3)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))

        policy.onSnapshot(facebookMarketplace, isFeedScreen = false)
        assertFalse(policy.onSnapshot(facebookFeed, isFeedScreen = true))

        scroll(facebookFeed, screens = 1)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    /**
     * Caught on the phone, not in here: leaving the app entirely disarms nothing by itself, because
     * an app we are not in sends no screens. Reopening Instagram on a spent budget blocked on sight
     * and threw the user straight back out - the opposite of the promise this rule makes.
     */
    @Test
    fun `coming back to the app later does not block until you scroll again`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 3)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))

        now += 30_000L
        assertFalse(policy.onSnapshot(facebookFeed, isFeedScreen = true))

        scroll(facebookFeed, screens = 1)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    /** Sitting still on an overspent feed is not scrolling it either. */
    @Test
    fun `the block lets go shortly after the scrolling stops`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 3)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))

        now += FeedBudgetPolicy.ARM_WINDOW_MS + 1
        assertFalse(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    @Test
    fun `another tab does not spend feed budget`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        policy.onSnapshot(facebookMarketplace, isFeedScreen = false)
        scroll(facebookMarketplace, screens = 10)
        assertFalse(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    /** Instagram DMs are the thing this app protects, so scrolling a thread is not feed scrolling. */
    @Test
    fun `instagram direct messages do not spend feed budget`() {
        policy.onSnapshot(instagramFeed, isFeedScreen = true)
        policy.onSnapshot(instagramDirectMessages, isFeedScreen = false)
        scroll(instagramDirectMessages, screens = 10)
        assertFalse(policy.onSnapshot(instagramFeed, isFeedScreen = true))
    }

    @Test
    fun `the budget refills after enough time away`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 3)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))

        now += budget.resetMinutes * 60_000L + 1
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 2)
        assertFalse(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    /** Stepping out of the app for a moment is not a break; the budget is per visit, not per screen. */
    @Test
    fun `a short trip out of the app does not refill the budget`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 3)
        now += 60_000L
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 1)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    @Test
    fun `each app has its own budget`() {
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 3)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))

        policy.onSnapshot(instagramFeed, isFeedScreen = true)
        assertFalse(policy.onSnapshot(instagramFeed, isFeedScreen = true))
    }

    @Test
    fun `scrolling an app the policy has never seen is harmless`() {
        policy.onScroll("com.example.unknown", screenHeight)
        assertFalse(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    @Test
    fun `a bigger budget takes more scrolling to spend`() {
        budget = FeedBudget(screens = 10, resetMinutes = 15)
        policy.onSnapshot(facebookFeed, isFeedScreen = true)
        scroll(facebookFeed, screens = 5)
        assertFalse(policy.onSnapshot(facebookFeed, isFeedScreen = true))
        scroll(facebookFeed, screens = 5)
        assertTrue(policy.onSnapshot(facebookFeed, isFeedScreen = true))
    }

    /** One screenful at a time, with the snapshots the service would be feeding it in between. */
    private fun scroll(on: ScreenSnapshot, screens: Int) {
        repeat(screens) {
            policy.onScroll(on.packageName, screenHeight)
            now += 100
        }
    }
}
