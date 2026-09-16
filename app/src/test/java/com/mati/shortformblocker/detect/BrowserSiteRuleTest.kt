package com.mati.shortformblocker.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whole-site blocking in browsers. The negative cases are the point: a domain must match itself and
 * its subdomains and nothing else, or blocking `x.com` takes `netflix.com` down with it.
 */
class BrowserSiteRuleTest {

    private val rules = RuleCatalog.ALL

    private fun chromeOn(url: String) = ScreenSnapshot(
        packageName = "com.android.chrome",
        viewIds = setOf("com.android.chrome:id/url_bar"),
        urlBarText = url,
    )

    @Test
    fun `social sites are blocked in a browser`() {
        listOf(
            "instagram.com",
            "https://www.instagram.com/someone/",
            "m.facebook.com/feed",
            "facebook.com",
            "x.com/home",
            "https://twitter.com/someone/status/123",
            "threads.net/@someone",
            "snapchat.com",
            "messenger.com/t/12345",
        ).forEach { url ->
            assertEquals(
                "expected $url to be blocked",
                RuleCatalog.BROWSER_SOCIAL_SITES,
                RuleMatcher.match(chromeOn(url), rules),
            )
        }
    }

    @Test
    fun `sites that merely contain a blocked domain are left alone`() {
        listOf(
            "netflix.com",
            "https://www.netflix.com/browse",
            "citrix.com",
            "linux.com/news",
            "notinstagram.com",
            "instagram.com.phishing.example",
            "myfacebook.com",
        ).forEach { url ->
            assertNull("expected $url to be left alone", RuleMatcher.match(chromeOn(url), rules))
        }
    }

    @Test
    fun `searching for a blocked site is not itself blocked`() {
        assertNull(RuleMatcher.match(chromeOn("google.com/search?q=instagram"), rules))
        assertNull(RuleMatcher.match(chromeOn("how to quit instagram"), rules))
    }

    @Test
    fun `ordinary browsing is untouched`() {
        listOf("wikipedia.org", "github.com/some/repo", "youtube.com/watch?v=abc", "bbc.co.uk")
            .forEach { url ->
                assertNull("expected $url to be left alone", RuleMatcher.match(chromeOn(url), rules))
            }
    }

    @Test
    fun `youtube stays partially allowed in the browser`() {
        assertNull(RuleMatcher.match(chromeOn("youtube.com"), rules))
        assertEquals(
            RuleCatalog.BROWSER_SHORT_FORM,
            RuleMatcher.match(chromeOn("youtube.com/shorts/abc123"), rules),
        )
    }

    @Test
    fun `tiktok is blocked by host now, not by substring`() {
        assertEquals(
            RuleCatalog.BROWSER_SHORT_FORM,
            RuleMatcher.match(chromeOn("https://www.tiktok.com/foryou"), rules),
        )
        assertNull(RuleMatcher.match(chromeOn("nottiktok.com"), rules))
    }

    @Test
    fun `host parsing handles what an address bar actually shows`() {
        assertEquals("instagram.com", BrowserUrlBars.hostOf("https://www.instagram.com/reels/"))
        assertEquals("instagram.com", BrowserUrlBars.hostOf("instagram.com"))
        assertEquals("m.facebook.com", BrowserUrlBars.hostOf("m.facebook.com/feed"))
        assertEquals("localhost.dev", BrowserUrlBars.hostOf("http://localhost.dev:8080/x"))
        assertNull(BrowserUrlBars.hostOf("search the web"))
        assertNull(BrowserUrlBars.hostOf("instagram"))
        assertNull(BrowserUrlBars.hostOf(""))
        assertNull(BrowserUrlBars.hostOf(null))
    }

    @Test
    fun `subdomains count but lookalikes do not`() {
        assertTrue(BrowserUrlBars.hostMatches("m.facebook.com", "facebook.com"))
        assertTrue(BrowserUrlBars.hostMatches("facebook.com", "facebook.com"))
        assertFalse(BrowserUrlBars.hostMatches("myfacebook.com", "facebook.com"))
        assertFalse(BrowserUrlBars.hostMatches("netflix.com", "x.com"))
        assertFalse(BrowserUrlBars.hostMatches(null, "x.com"))
    }

    @Test
    fun `the in-app rules are unaffected by site blocking`() {
        val instagramApp = ScreenSnapshot(
            packageName = "com.instagram.android",
            viewIds = setOf("com.instagram.android:id/feed_container"),
            selectedLabels = setOf("Home"),
        )
        assertNull(RuleMatcher.match(instagramApp, rules))
    }
}
