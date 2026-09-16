package com.mati.shortformblocker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.mati.shortformblocker.BlockerApp
import com.mati.shortformblocker.data.BlockerSettings
import com.mati.shortformblocker.detect.BlockRule
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

    /** Where the blocked screen was reached from, recorded with the evidence (view ids only). */
    private var previousScreen: ScreenSnapshot? = null
    private var evaluations = 0
    private var clearedScreens = 0
    private var eventsReceived = 0
    private var nullRoots = 0
    private var connectedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        connectedAt = SystemClock.uptimeMillis()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        overlay = BlockOverlay(this)
        enforcer = Enforcer(
            service = this,
            overlay = overlay,
            onBlockStarted = ::recordBlock,
            onRecheckNeeded = ::evaluate,
            blockCountProvider = { todayBlockCount },
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

    /** A scroll in the reels pager means the next reel, which is not the one a friend sent. */
    private fun handleScroll(event: AccessibilityEvent) {
        if (event.packageName?.toString() != InstagramSurfaces.PACKAGE) return
        val source = event.source
        val sourceId = source?.viewIdResourceName
        if (InstagramSurfaces.isClipsScrollSource(sourceId)) reelPolicy.onClipsScroll()
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
        val effectiveMatch = if (isReelScreen && allowedByPass) null else match

        SnapshotHolder.record(snapshot, effectiveMatch?.id)
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
        // The last snapshot is deliberately kept: OEM builds rebind this service constantly, and
        // wiping it here is what made the debug screen useless exactly when it was needed.
        scope.cancel()
    }

    companion object {
        const val TAG = "ShortFormBlocker"

        private const val MIN_EVALUATION_INTERVAL_MS = 150L
        private const val NOTIFICATION_TIMEOUT_MS = 100L

        @Volatile
        var instance: BlockerAccessibilityService? = null
            private set
    }
}
