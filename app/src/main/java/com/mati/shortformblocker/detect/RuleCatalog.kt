package com.mati.shortformblocker.detect

/**
 * Every rule the app ships with. This is the file to edit when an app redesign breaks detection:
 * capture the new screen with the in-app debug screen, then add the view id here.
 */
object RuleCatalog {

    val YOUTUBE_SHORTS = BlockRule(
        id = "youtube_shorts",
        displayName = "YouTube Shorts",
        description = "Blocks the Shorts player and Shorts tab. Normal videos, search and " +
            "subscriptions keep working.",
        packages = setOf("com.google.android.youtube", "com.google.android.youtube.tvkids"),
        // Deliberately excludes the Shorts shelf on the home feed: matching that would bounce you
        // out of the home feed itself. Only the full-screen Shorts player counts.
        viewIdContains = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "reel_progress_bar",
            "shorts_player",
        ),
        selectedLabelContains = listOf("Shorts"),
    )

    val INSTAGRAM_REELS = BlockRule(
        id = "instagram_reels",
        displayName = "Instagram Reels",
        description = "Blocks the Reels tab and the feed. A reel a friend sends you in a DM still " +
            "plays - but scrolling on from it is blocked.",
        packages = setOf("com.instagram.android", "com.instagram.lite"),
        viewIdContains = listOf(
            "clips_viewer",
            "clips_swipe_refresh_container",
            "clips_video_container",
            "reels_viewer",
        ),
        selectedLabelContains = listOf("Reels"),
        // Instagram keeps the Reels page of its swipeable pager attached behind the home feed, so
        // every screen of the app reports these ids. Only the ones on screen count.
        visibleSignalsOnly = true,
    )

    val TIKTOK = BlockRule(
        id = "tiktok",
        displayName = "TikTok",
        description = "Blocks the entire app - all of it is short form.",
        packages = setOf(
            "com.zhiliaoapp.musically",
            "com.zhiliaoapp.musically.go",
            "com.ss.android.ugc.trill",
            "com.ss.android.ugc.aweme",
        ),
        mode = BlockMode.WHOLE_APP,
    )

    val FACEBOOK_REELS = BlockRule(
        id = "facebook_reels",
        displayName = "Facebook Reels",
        description = "Blocks the Reels tab in Facebook. The rest of the app keeps working.",
        packages = setOf("com.facebook.katana", "com.facebook.lite"),
        viewIdContains = listOf("reels_viewer", "reels_tab", "video_home_reels"),
        selectedLabelContains = listOf("Reels"),
    )

    val SNAPCHAT_SPOTLIGHT = BlockRule(
        id = "snapchat_spotlight",
        displayName = "Snapchat Spotlight",
        description = "Blocks the Spotlight feed. Chat, camera and stories keep working.",
        packages = setOf("com.snapchat.android"),
        viewIdContains = listOf("spotlight_page", "spotlight_feed"),
        selectedLabelContains = listOf("Spotlight"),
    )

    val BROWSER_SHORT_FORM = BlockRule(
        id = "browser_short_form",
        displayName = "Short form in browsers",
        description = "Blocks youtube.com/shorts, instagram.com/reel, tiktok.com and friends in " +
            "Chrome, Firefox, Edge, Brave, Samsung Internet and others.",
        packages = BrowserUrlBars.BROWSER_PACKAGES,
        urlContains = BrowserUrlBars.SHORT_FORM_URL_FRAGMENTS,
        urlHostEquals = listOf("tiktok.com", "fb.watch"),
    )

    val BROWSER_SOCIAL_SITES = BlockRule(
        id = "browser_social_sites",
        displayName = "Social sites in browsers",
        description = "Blocks instagram.com, facebook.com, x.com, threads and snapchat.com in any " +
            "browser. The web versions are the easy way around the in-app rules.",
        packages = BrowserUrlBars.BROWSER_PACKAGES,
        urlHostEquals = listOf(
            "instagram.com",
            "facebook.com",
            "fb.com",
            "fb.watch",
            "messenger.com",
            "x.com",
            "twitter.com",
            "threads.net",
            "threads.com",
            "snapchat.com",
        ),
    )

    val REDDIT_VIDEO = BlockRule(
        id = "reddit_video",
        displayName = "Reddit video feed",
        description = "Blocks the full-screen video feed. Off by default - Reddit renames its " +
            "view ids often, so confirm it does not interrupt normal browsing before trusting it.",
        packages = setOf("com.reddit.frontpage"),
        viewIdContains = listOf("video_feed", "watch_feed"),
        selectedLabelContains = listOf("Watch"),
        enabledByDefault = false,
    )

    val X_VIDEO = BlockRule(
        id = "x_video",
        displayName = "X (Twitter) video tab",
        description = "Blocks the immersive video feed. Off by default - higher false-positive risk.",
        packages = setOf("com.twitter.android"),
        viewIdContains = listOf("immersive_player", "immersive_recycler"),
        selectedLabelContains = listOf("Video"),
        enabledByDefault = false,
    )

    val ALL: List<BlockRule> = listOf(
        YOUTUBE_SHORTS,
        INSTAGRAM_REELS,
        TIKTOK,
        FACEBOOK_REELS,
        SNAPCHAT_SPOTLIGHT,
        BROWSER_SHORT_FORM,
        BROWSER_SOCIAL_SITES,
        REDDIT_VIDEO,
        X_VIDEO,
    )

    /** Packages the accessibility service subscribes to - no other app is ever inspected. */
    val ALL_PACKAGES: Set<String> = ALL.flatMap { it.packages }.toSet()

    val DEFAULT_DISABLED_IDS: Set<String> =
        ALL.filterNot { it.enabledByDefault }.map { it.id }.toSet()

    fun byId(id: String): BlockRule? = ALL.firstOrNull { it.id == id }
}
