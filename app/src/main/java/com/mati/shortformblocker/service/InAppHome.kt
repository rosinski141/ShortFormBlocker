package com.mati.shortformblocker.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.mati.shortformblocker.detect.InstagramSurfaces

/**
 * Escalation that stays inside the blocked app.
 *
 * Throwing someone out to the launcher is a blunt instrument: in Instagram, Reels sits next to DMs
 * and the feed, so the useful move is to drop them back on the Home tab rather than close the app
 * they may have opened to answer a message. Apps with no such tab - TikTok, where everything is
 * short form - are not handled here and keep falling through to the device home screen.
 */
object InAppHome {

    /** @return true when the app was actually navigated home, false to fall back to Home. */
    fun goToAppHome(service: AccessibilityService, packageName: String?): Boolean {
        if (packageName !in InstagramSurfaces.PACKAGES) return false
        val root = service.rootInActiveWindow ?: return false
        // Our own overlay can win the active window on some builds; clicking blind into it would
        // hit nothing, so hand those cases back to the device home screen instead.
        if (root.packageName?.toString() != packageName) {
            root.recycleIfNeeded()
            return false
        }
        return try {
            InstagramSurfaces.HOME_TAB_ID_FRAGMENTS.any { fragment ->
                clickNodeWithId(root, "$packageName:id/$fragment")
            }
        } finally {
            root.recycleIfNeeded()
        }
    }

    private fun clickNodeWithId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }
            .getOrNull()
            .orEmpty()
        return try {
            nodes.any { node -> node.isVisibleToUser && clickSelfOrParent(node) }
        } finally {
            nodes.forEach { if (it !== root) it.recycleIfNeeded() }
        }
    }

    /** The tab id often sits on a plain container whose clickable ancestor is a few levels up. */
    private fun clickSelfOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < MAX_PARENT_HOPS) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                if (current !== node) current.recycleIfNeeded()
                return true
            }
            val parent = current.parent
            if (current !== node) current.recycleIfNeeded()
            current = parent
            depth++
        }
        if (current !== node) current?.recycleIfNeeded()
        return false
    }

    /** `recycle()` is a no-op from API 33 onwards, but is still needed on older releases. */
    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runCatching { recycle() }
        }
    }

    /** How far up from the tab node to look for something that actually takes a click. */
    private const val MAX_PARENT_HOPS = 4
}
