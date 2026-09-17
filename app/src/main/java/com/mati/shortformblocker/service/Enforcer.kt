package com.mati.shortformblocker.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.mati.shortformblocker.detect.BlockMode
import com.mati.shortformblocker.detect.BlockRule
import com.mati.shortformblocker.detect.ScreenSnapshot

/**
 * Decides what actually happens when a rule fires.
 *
 * Escalation: show the block card and press Back; if the screen still matches, press Back once
 * more; if it *still* matches, leave the feed the bluntest way available. In Instagram that means
 * tapping the Home tab, so the user keeps the app they may have opened to answer a DM; everywhere
 * else, and whenever that tab cannot be found, it means the device home screen. Apps that intercept
 * Back (TikTok on its feed) fall through within a second.
 */
class Enforcer(
    private val service: AccessibilityService,
    private val overlay: BlockOverlay,
    private val onBlockStarted: (BlockRule, ScreenSnapshot) -> Unit,
    private val onRecheckNeeded: () -> Unit,
    private val blockCountProvider: () -> Int,
    /** How long out of the app earns a fresh feed budget, for the block card to quote. */
    private val feedResetMinutesProvider: () -> Int,
) {

    private val handler = Handler(Looper.getMainLooper())

    private var episodeRuleId: String? = null
    private var episodePackage: String? = null
    private var backAttempts = 0
    private var lastActionAt = 0L

    // Counting state, deliberately *not* cleared by resetEpisode(): bouncing out of a feed produces
    // several match/clear cycles a second apart, and each one is the same block to a human.
    private var lastCountedRuleId: String? = null
    private var lastCountedAt = 0L

    private val recheck = Runnable { onRecheckNeeded() }
    private val hideOverlay = Runnable {
        overlay.hide()
        resetEpisode()
    }

    /**
     * Last resort. If the blocked app somehow stops sending events - or the overlay itself becomes
     * the active window, so re-checks stop seeing the app behind it - this guarantees the card
     * always comes down instead of leaving the phone covered by a green screen.
     */
    private val forceHide = Runnable {
        overlay.hide()
        resetEpisode()
    }

    fun onMatch(rule: BlockRule, snapshot: ScreenSnapshot) {
        handler.removeCallbacks(hideOverlay)
        val now = SystemClock.uptimeMillis()

        val sameEpisode = rule.id == episodeRuleId &&
            snapshot.packageName == episodePackage &&
            now - lastActionAt < EPISODE_WINDOW_MS
        if (!sameEpisode) {
            episodeRuleId = rule.id
            episodePackage = snapshot.packageName
            backAttempts = 0
        }
        if (rule.id != lastCountedRuleId || now - lastCountedAt > EPISODE_WINDOW_MS) {
            onBlockStarted(rule, snapshot)
        }
        lastCountedRuleId = rule.id
        lastCountedAt = now
        lastActionAt = now

        val isFeed = rule.mode == BlockMode.BUDGETED_FEED
        overlay.show(
            title = if (isFeed) rule.displayName + " closed" else rule.displayName + " blocked",
            subtitle = if (isFeed) {
                "That is the feed for this visit. It opens again once you have left the feed " +
                    "alone for ${feedResetMinutesProvider()} minutes."
            } else {
                "Not today. Go do the thing you actually opened your phone for."
            },
            footer = blockCountProvider().let { count ->
                if (count <= 0) "Blocked by ShortFormBlocker" else "$count blocks today"
            },
        )

        if (backAttempts < MAX_BACK_ATTEMPTS) {
            backAttempts++
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        } else {
            backAttempts = 0
            // Dropping someone on the app's Home tab is the kind thing to do for a reels block -
            // but for a feed block that tab *is* the feed, so the only way out is out of the app.
            val stayedInApp = !isFeed && InAppHome.goToAppHome(service, snapshot.packageName)
            if (!stayedInApp) service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }

        handler.removeCallbacks(recheck)
        handler.postDelayed(recheck, RECHECK_DELAY_MS)
        handler.removeCallbacks(forceHide)
        handler.postDelayed(forceHide, MAX_OVERLAY_MS)
    }

    /** Called for every screen that does not match, including screens in other apps. */
    fun onClear() {
        handler.removeCallbacks(recheck)
        handler.removeCallbacks(forceHide)
        if (overlay.isShowing) {
            handler.removeCallbacks(hideOverlay)
            handler.postDelayed(hideOverlay, OVERLAY_LINGER_MS)
        } else {
            resetEpisode()
        }
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        overlay.hide()
        resetEpisode()
    }

    private fun resetEpisode() {
        episodeRuleId = null
        episodePackage = null
        backAttempts = 0
    }

    companion object {
        /** How long to wait before looking at the screen again after pressing Back. */
        const val RECHECK_DELAY_MS = 350L

        /** Back presses before falling back to Home. */
        const val MAX_BACK_ATTEMPTS = 2

        /** Keeps the card up briefly after the screen clears, so the message is readable. */
        const val OVERLAY_LINGER_MS = 1_200L

        /** Repeat matches inside this window count as one block, not several. */
        const val EPISODE_WINDOW_MS = 10_000L

        /** Absolute cap on how long the block card can stay on screen. */
        const val MAX_OVERLAY_MS = 6_000L
    }
}
