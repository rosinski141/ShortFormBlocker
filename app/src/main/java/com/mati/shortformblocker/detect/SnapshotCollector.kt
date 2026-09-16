package com.mati.shortformblocker.detect

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Turns the live accessibility node tree into a [ScreenSnapshot].
 *
 * The walk is bounded (breadth-first, capped in both node count and depth) because it runs on every
 * window change in apps that redraw constantly; an unbounded walk of a Reels feed is expensive
 * enough to make the phone stutter.
 */
object SnapshotCollector {

    const val MAX_NODES = 500
    const val MAX_DEPTH = 28

    fun collect(root: AccessibilityNodeInfo?): ScreenSnapshot? {
        val packageName = root?.packageName?.toString() ?: return null

        val viewIds = HashSet<String>()
        val visibleViewIds = HashSet<String>()
        val texts = HashSet<String>()
        val contentDescriptions = HashSet<String>()
        val selectedLabels = HashSet<String>()
        val visibleSelectedLabels = HashSet<String>()
        val visibleDescriptions = HashSet<String>()
        val isBrowser = packageName in BrowserUrlBars.BROWSER_PACKAGES
        var urlBarText: String? = null
        var visited = 0
        var truncated = false

        val queue = ArrayDeque<Node>()
        queue.addLast(Node(root, 0))

        while (queue.isNotEmpty()) {
            if (visited >= MAX_NODES) {
                truncated = true
                break
            }
            val (node, depth) = queue.removeFirst()
            visited++

            val viewId = node.viewIdResourceName
            val visible = node.isVisibleToUser
            if (viewId != null) {
                viewIds.add(viewId)
                if (visible) visibleViewIds.add(viewId)
            }
            val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val description = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            if (text != null) texts.add(text)
            if (description != null) contentDescriptions.add(description)
            if (description != null && visible) visibleDescriptions.add(description)
            if (node.isSelected) {
                text?.let { selectedLabels.add(it) }
                description?.let { selectedLabels.add(it) }
                if (visible) {
                    text?.let { visibleSelectedLabels.add(it) }
                    description?.let { visibleSelectedLabels.add(it) }
                }
            }
            if (isBrowser && urlBarText == null && viewId != null && BrowserUrlBars.isUrlBar(viewId)) {
                urlBarText = text?.lowercase()
            }

            if (depth < MAX_DEPTH) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    queue.addLast(Node(child, depth + 1))
                }
            } else {
                truncated = true
            }

            if (node !== root) node.recycleIfNeeded()
        }

        queue.forEach { if (it.node !== root) it.node.recycleIfNeeded() }

        return ScreenSnapshot(
            packageName = packageName,
            viewIds = viewIds,
            visibleViewIds = visibleViewIds,
            texts = texts,
            contentDescriptions = contentDescriptions,
            selectedLabels = selectedLabels,
            visibleSelectedLabels = visibleSelectedLabels,
            visibleContentDescriptions = visibleDescriptions,
            urlBarText = urlBarText,
            nodeCount = visited,
            truncated = truncated,
        )
    }

    private data class Node(val node: AccessibilityNodeInfo, val depth: Int)

    /** `recycle()` is a no-op from API 33 onwards, but is still needed on older releases. */
    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            runCatching { recycle() }
        }
    }
}
