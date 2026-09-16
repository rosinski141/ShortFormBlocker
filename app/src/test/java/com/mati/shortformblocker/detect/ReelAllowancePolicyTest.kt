package com.mati.shortformblocker.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule being tested: a reel a friend sent you plays; the feed behind it does not.
 */
class ReelAllowancePolicyTest {

    private var now = 0L
    private val policy = ReelAllowancePolicy(clock = { now })

    private fun dmThread() = ScreenSnapshot(
        packageName = InstagramSurfaces.PACKAGE,
        viewIds = setOf("com.instagram.android:id/thread_message_list"),
    )

    private fun homeFeed() = ScreenSnapshot(
        packageName = InstagramSurfaces.PACKAGE,
        viewIds = setOf("com.instagram.android:id/feed_container", "com.instagram.android:id/clips_tab"),
        visibleViewIds = setOf(
            "com.instagram.android:id/feed_container",
            "com.instagram.android:id/clips_tab",
        ),
        selectedLabels = setOf("Home"),
        visibleSelectedLabels = setOf("Home"),
    )

    private fun reelViewer(vararg authors: String) = ScreenSnapshot(
        packageName = InstagramSurfaces.PACKAGE,
        viewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
        visibleViewIds = setOf("com.instagram.android:id/clips_viewer_view_pager"),
        visibleContentDescriptions = authors
            .map { "Reel by $it. Double-tap to play or pause." }
            .toSet(),
    )

    private fun reelsTab(author: String) = ScreenSnapshot(
        packageName = InstagramSurfaces.PACKAGE,
        viewIds = setOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_tab",
        ),
        visibleViewIds = setOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_tab",
        ),
        selectedLabels = setOf("Reels"),
        visibleSelectedLabels = setOf("Reels"),
        visibleContentDescriptions = setOf("Reel by $author. Double-tap to play or pause."),
    )

    private fun allow(snapshot: ScreenSnapshot, isReel: Boolean = true) =
        policy.onSnapshot(snapshot, isReel)

    @Test
    fun `reel opened from a dm is allowed`() {
        allow(dmThread(), isReel = false)
        assertTrue(allow(reelViewer("friendsreel")))
    }

    @Test
    fun `the same reel keeps playing`() {
        allow(dmThread(), isReel = false)
        allow(reelViewer("friendsreel"))
        now += 5_000
        assertTrue(allow(reelViewer("friendsreel")))
        assertTrue(allow(reelViewer("friendsreel")))
    }

    @Test
    fun `scrolling to the next reel is blocked`() {
        allow(dmThread(), isReel = false)
        allow(reelViewer("friendsreel"))
        now += ReelAllowancePolicy.SCROLL_GRACE_MS + 1
        policy.onClipsScroll()
        assertFalse(allow(reelViewer("somebodyelse")))
    }

    @Test
    fun `the reel changing is blocked even without a scroll event`() {
        allow(dmThread(), isReel = false)
        allow(reelViewer("friendsreel"))
        assertFalse(allow(reelViewer("thealgorithm")))
    }

    @Test
    fun `mid-swipe with both reels visible does not end the pass`() {
        allow(dmThread(), isReel = false)
        allow(reelViewer("friendsreel"))
        assertTrue(allow(reelViewer("friendsreel", "nextone")))
    }

    @Test
    fun `the settling scroll as the viewer opens does not end the pass`() {
        allow(dmThread(), isReel = false)
        allow(reelViewer("friendsreel"))
        now += ReelAllowancePolicy.SCROLL_GRACE_MS - 1
        policy.onClipsScroll()
        assertTrue(allow(reelViewer("friendsreel")))
    }

    @Test
    fun `swiping back to the shared reel does not revive the pass`() {
        allow(dmThread(), isReel = false)
        allow(reelViewer("friendsreel"))
        assertFalse(allow(reelViewer("thealgorithm")))
        assertFalse(allow(reelViewer("friendsreel")))
    }

    @Test
    fun `going back to the conversation earns a fresh pass for the next reel`() {
        allow(dmThread(), isReel = false)
        allow(reelViewer("firstfriend"))
        assertFalse(allow(reelViewer("thealgorithm")))

        allow(dmThread(), isReel = false)
        assertTrue(allow(reelViewer("secondfriend")))
    }

    @Test
    fun `the reels tab is never allowed even straight after a dm`() {
        allow(dmThread(), isReel = false)
        assertFalse(allow(reelsTab("whoever")))
    }

    @Test
    fun `a reel opened from anywhere but a dm is blocked`() {
        allow(homeFeed(), isReel = false)
        assertFalse(allow(reelViewer("explorereel")))
    }

    @Test
    fun `leaving instagram clears the pass`() {
        allow(dmThread(), isReel = false)
        allow(reelViewer("friendsreel"))
        allow(ScreenSnapshot(packageName = "com.whatsapp"), isReel = false)
        assertFalse(allow(reelViewer("friendsreel")))
    }

    @Test
    fun `a pass with no author label still ends on scroll`() {
        allow(dmThread(), isReel = false)
        assertTrue(allow(reelViewer()))
        now += ReelAllowancePolicy.SCROLL_GRACE_MS + 1
        policy.onClipsScroll()
        assertFalse(allow(reelViewer()))
    }

    @Test
    fun `the home feed is not mistaken for a dm screen`() {
        // The bottom nav carries a DM button on every screen; only a thread counts.
        val home = ScreenSnapshot(
            packageName = InstagramSurfaces.PACKAGE,
            viewIds = setOf(
                "com.instagram.android:id/feed_tab",
                "com.instagram.android:id/direct_tab",
                "com.instagram.android:id/clips_tab",
            ),
            selectedLabels = setOf("Home"),
        )
        assertFalse(InstagramSurfaces.isDirectMessageScreen(home))
        allow(home, isReel = false)
        assertFalse(allow(reelViewer("explorereel")))
    }

    @Test
    fun `a dm thread is recognised`() {
        assertTrue(InstagramSurfaces.isDirectMessageScreen(dmThread()))
    }

    @Test
    fun `author is parsed out of the accessibility label`() {
        assertEquals(
            "wolfeyemedia",
            InstagramSurfaces.reelAuthor("Reel by wolfeyemedia. Double-tap to play or pause."),
        )
        assertEquals(null, InstagramSurfaces.reelAuthor("Photo by someone"))
    }

    @Test
    fun `comment sheet scrolls do not end the pass`() {
        assertFalse(InstagramSurfaces.isClipsScrollSource("com.instagram.android:id/comment_thread_recycler"))
        assertTrue(InstagramSurfaces.isClipsScrollSource("com.instagram.android:id/clips_viewer_view_pager"))
    }
}
