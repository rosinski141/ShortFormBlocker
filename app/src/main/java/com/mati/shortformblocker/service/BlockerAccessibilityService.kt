package com.mati.shortformblocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mati.shortformblocker.BlockerApp
import com.mati.shortformblocker.data.BlockerSettings
import com.mati.shortformblocker.detect.BlockMode
import com.mati.shortformblocker.detect.BlockRule
import com.mati.shortformblocker.detect.FeedBudgetHolder
import com.mati.shortformblocker.detect.FeedBudgetPolicy
import com.mati.shortformblocker.detect.InstagramSurfaces
import com.mati.shortformblocker.detect.ReelAllowancePolicy
import com.mati.shortformblocker.detect.RuleCatalog
import com.mati.shortformblocker.detect.RuleMatcher
import com.mati.shortformblocker.detect.ScreenSnapshot
import com.mati.shortformblocker.detect.SnapshotCollector
import com.mati.shortformblocker.detect.SnapshotHolder
import com.mati.shortformblocker.detect.idsOnlySummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The eye of the app: reads the foreground window, matches it against the active rules and hands
 * matches to [Enforcer].
 *
 * It only ever subscribes to packages that a rule targets (see [RuleCatalog.ALL_PACKAGES]), so no
 * other app's screen content is inspected. The one exception is the debug capture mode, which the
 * user turns on explicitly to grab view ids from an app whose rule has stopped working.
 */
class BlockerAccessibilityService : AccessibilityService() {

    private lateinit var overlay: BlockOverlay
    private lateinit var enforcer: Enforcer

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var settings: BlockerSettings = BlockerSettings()

    @Volatile
    private var todayBlockCount: Int = 0

    @Volatile
    var captureAllPackages: Boolean = false
        private set

    private var lastEvaluationAt = 0L

    /** Lets a reel a friend sent you play, while still blocking the feed behind it. */
    private val reelPolicy = ReelAllowancePolicy()

    /** Closes the home feeds once a visit has scrolled through its budget. */
    private lateinit var feedPolicy: FeedBudgetPolicy

    /** Screen height in pixels, which is the unit the feed budget is counted in. */
    private var screenHeightPx = 0

    /** Where the blocked screen was reached from, recorded with the evidence (view ids only). */
    private var previousScreen: ScreenSnapshot? = null
    private var evaluations = 0
    private var clearedScreens = 0
    private var eventsReceived = 0
    private var scrollEvents = 0
    private var reportedScrolls = 0
    private var unreportedScrolls = 0
    private var nullRoots = 0
    private var connectedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connectedAt = SystemClock.uptimeMillis()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        screenHeightPx = resources.displayMetrics.heightPixels
        feedPolicy = FeedBudgetPolicy(
            screenHeightPx = screenHeightPx,
            budget = { settings.feedBudgetAt(System.currentTimeMillis()) },
        )
        overlay = BlockOverlay(this)
        enforcer = Enforcer(
            service = this,
            overlay = overlay,
            onBlockStarted = ::recordBlock,
            onRecheckNeeded = ::evaluate,
            blockCountProvider = { todayBlockCount },
            feedResetMinutesProvider = {
                settings.feedBudgetAt(System.currentTimeMillis()).resetMinutes
            },
        )
        applyServiceInfo()

        val app = BlockerApp.from(this)
        scope.launch {
            app.settings.settings.collect { settings = it }
        }
        scope.launch {
            app.stats.stats.collect { todayBlockCount = it.today }
        }

        ProtectionService.start(this)
        Log.i(TAG, "Accessibility service connected, watching ${RuleCatalog.ALL_PACKAGES.size} packages")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        eventsReceived++
        if (type == AccessibilityEvent.TYPE_VIEW_SCROLLED) handleScroll(event)
        val now = SystemClock.uptimeMillis()
        // Content-change events fire continuously in a video feed; window changes never get skipped.
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            now - lastEvaluationAt < MIN_EVALUATION_INTERVAL_MS
        ) {
            return
        }
        lastEvaluationAt = now
        evaluate()
    }

    /**
     * Scrolling means two things here: in the reels pager it is the next reel, which is not the one
     * a friend sent, and in a home feed it is budget being spent.
     */
    private fun handleScroll(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        scrollEvents++
        feedPolicy.onScroll(packageName, scrollDistancePx(event))
        if (packageName != InstagramSurfaces.PACKAGE) return
        val source = event.source
        val sourceId = source?.viewIdResourceName
        if (InstagramSurfaces.isClipsScrollSource(sourceId)) reelPolicy.onClipsScroll()
    }

    /**
     * How far this scroll moved the list. Not every view reports a delta - Facebook's feed only
     * sometimes does - and counting an unreported scroll as nothing would make the budget
     * unspendable, so it falls back to a conservative fraction of a screen. The cap keeps a single
     * absurd event from swallowing the whole budget.
     *
     * A scroll that only moved sideways - a stories tray, a photo carousel inside a post - reports
     * no vertical delta on purpose, not because the view failed to report one. That must not fall
     * into the "unreported" fallback, or every side-swipe would spend budget as if it were half a
     * screen of feed.
     */
    private fun scrollDistancePx(event: AccessibilityEvent): Int {
        val deltaY = kotlin.math.abs(event.scrollDeltaY)
        val deltaX = kotlin.math.abs(event.scrollDeltaX)
        if (deltaY < MIN_REPORTED_SCROLL_PX) {
            if (deltaX >= MIN_REPORTED_SCROLL_PX) return 0
            unreportedScrolls++
            return screenHeightPx / UNREPORTED_SCROLL_DIVISOR
        }
        reportedScrolls++
        return deltaY.coerceAtMost(screenHeightPx * MAX_SCREENS_PER_SCROLL)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    /** Lets the debug screen widen the net to any app, to capture view ids for a broken rule. */
    fun setCaptureAllPackages(enabled: Boolean) {
        captureAllPackages = enabled
        applyServiceInfo()
    }

    private fun evaluate() {
        val rules = settings.activeRules(System.currentTimeMillis())
        val snapshot = SnapshotCollector.collect(rootInActiveWindow)
        if (snapshot == null) {
            nullRoots++
            return
        }
        if (snapshot.packageName == packageName) {
            // Our own UI (or our own block overlay) is the active window: nothing to enforce, and
            // clearing here stops the card from hanging around if the overlay hides the app behind it.
            enforcer.onClear()
            return
        }

        evaluations++
        val match: BlockRule? = RuleMatcher.match(snapshot, rules)
        val isReelScreen = match?.id == RuleCatalog.INSTAGRAM_REELS.id
        val allowedByPass = reelPolicy.onSnapshot(snapshot, isReelScreen)
        val isFeedScreen = match?.mode == BlockMode.BUDGETED_FEED
        val feedOverBudget = feedPolicy.onSnapshot(snapshot, isFeedScreen)
        val effectiveMatch = when {
            isReelScreen && allowedByPass -> null
            isFeedScreen -> match.takeIf { feedOverBudget }
            match != null -> match
            // The feed rule did not fire for this screen - Facebook hides its tab bar mid-fling -
            // but the policy still places us in a feed that is out of budget.
            feedOverBudget -> rules.feedRuleFor(snapshot.packageName)
            else -> null
        }

        SnapshotHolder.record(
            snapshot = snapshot,
            matchedRuleId = effectiveMatch?.id,
            policyNote = "rule on screen: ${match?.id ?: "none"}, scrolls: $scrollEvents " +
                "($reportedScrolls measured, $unreportedScrolls estimated), " +
                "feed budget: ${feedPolicy.describe(snapshot.packageName)}",
        )
        FeedBudgetHolder.record(feedPolicy.states())
        if (effectiveMatch == null) {
            previousScreen = snapshot
            clearedScreens++
        }

        if (effectiveMatch != null) {
            enforcer.onMatch(effectiveMatch, snapshot)
        } else {
            enforcer.onClear()
        }
    }

    private fun recordBlock(rule: BlockRule, snapshot: ScreenSnapshot) {
        Log.i(TAG, "Blocked ${rule.id} on ${snapshot.packageName}")
        val app = BlockerApp.from(this)
        // The dump is kept so the debug screen can answer "why did it fire on *that* screen?" even
        // after the service has been restarted.
        // Header first: a dump of a busy screen can run past the storage cap, and the diagnostic
        // lines are worth more than the tail of the node list.
        val evidence = buildString {
            appendLine("rule: ${rule.id}")
            appendLine("reel pass: ${reelPolicy.describe()}")
            appendLine("feed budget: ${feedPolicy.describe(snapshot.packageName)}")
            appendLine("screens evaluated: $evaluations, of which not blocked: $clearedScreens")
            appendLine(
                "events: $eventsReceived, empty windows: $nullRoots, " +
                    "service age: ${(SystemClock.uptimeMillis() - connectedAt) / 1000}s",
            )
            appendLine()
            appendLine("--- screen this was entered from (view ids only) ---")
            appendLine(previousScreen?.idsOnlySummary() ?: "(none captured)")
            appendLine("--- blocked screen ---")
            append(snapshot.describe())
        }
        scope.launch { app.stats.recordBlock(rule.id, evidence) }
    }

    private fun applyServiceInfo() {
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
            AccessibilityEvent.TYPE_VIEW_SCROLLED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = info.flags or
            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        info.notificationTimeout = NOTIFICATION_TIMEOUT_MS
        info.packageNames = if (captureAllPackages) null else RuleCatalog.ALL_PACKAGES.toTypedArray()
        serviceInfo = info
    }

    private fun teardown() {
        if (instance === this) instance = null
        if (::enforcer.isInitialized) enforcer.shutdown()
        reelPolicy.reset()
        if (::feedPolicy.isInitialized) feedPolicy.reset()
        // Unlike the snapshot below, this one has to go: it is a countdown against a budget that
        // has just been forgotten, so leaving it up would lock the user out of a feed that is open.
        FeedBudgetHolder.clear()
        // The last snapshot is deliberately kept: OEM builds rebind this service constantly, and
        // wiping it here is what made the debug screen useless exactly when it was needed.
        scope.cancel()
    }

    /** The budgeted-feed rule for this app, if one is enabled. */
    private fun List<BlockRule>.feedRuleFor(packageName: String): BlockRule? =
        firstOrNull { it.mode == BlockMode.BUDGETED_FEED && packageName in it.packages }

    companion object {
        const val TAG = "ShortFormBlocker"

        private const val MIN_EVALUATION_INTERVAL_MS = 150L
        private const val NOTIFICATION_TIMEOUT_MS = 100L

        /** Below this, treat the event as reporting no distance at all rather than a 1px scroll. */
        private const val MIN_REPORTED_SCROLL_PX = 2

        /** What a scroll that reports no distance is worth: half a screen. */
        private const val UNREPORTED_SCROLL_DIVISOR = 2

        /** Ceiling on a single scroll event, in screens. */
        private const val MAX_SCREENS_PER_SCROLL = 3

        @Volatile
        var instance: BlockerAccessibilityService? = null
            private set
    }
}
