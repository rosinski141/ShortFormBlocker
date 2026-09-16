package com.mati.shortformblocker.detect

/**
 * A flattened, Android-free description of what is on screen right now.
 *
 * Everything the detector needs lives in here so that rule matching stays a pure function that can
 * be unit tested on the JVM. Snapshots are never persisted and never leave the device.
 */
data class ScreenSnapshot(
    val packageName: String,
    /** `viewIdResourceName` of every node walked, e.g. `com.google.android.youtube:id/reel_recycler`. */
    val viewIds: Set<String> = emptySet(),
    /**
     * The subset of [viewIds] belonging to nodes actually on screen. Instagram keeps the Reels page
     * of its swipeable pager attached while you are on the home feed, so the full id set says
     * "reels" on every screen of the app; this is what tells the two apart.
     */
    val visibleViewIds: Set<String> = emptySet(),
    /** Visible text of every node walked. */
    val texts: Set<String> = emptySet(),
    /** Content descriptions of every node walked. */
    val contentDescriptions: Set<String> = emptySet(),
    /**
     * Labels of nodes reporting `isSelected`. Bottom-nav tabs set this, which is how we tell
     * "the Reels tab is open" apart from "the Reels tab button exists on every screen" - the
     * difference between a working blocker and one that bricks the whole app.
     */
    val selectedLabels: Set<String> = emptySet(),
    /** [selectedLabels] of visible nodes only - the retained pager page reports a selection too. */
    val visibleSelectedLabels: Set<String> = emptySet(),
    /**
     * Content descriptions of nodes that are actually visible on screen. A pager preloads its
     * neighbours, so the full tree can describe reels you cannot see; this is the subset that tells
     * you which reel is really in front of you.
     */
    val visibleContentDescriptions: Set<String> = emptySet(),
    /** Address-bar contents, lowercased, when the foreground app is a browser. */
    val urlBarText: String? = null,
    val nodeCount: Int = 0,
    val truncated: Boolean = false,
) {
    fun containsViewIdFragment(fragment: String): Boolean =
        viewIds.any { it.contains(fragment, ignoreCase = true) }

    fun containsVisibleViewIdFragment(fragment: String): Boolean =
        visibleViewIds.any { it.contains(fragment, ignoreCase = true) }

    fun hasSelectedLabel(label: String): Boolean =
        selectedLabels.any { it.contains(label, ignoreCase = true) }

    fun hasVisibleSelectedLabel(label: String): Boolean =
        visibleSelectedLabels.any { it.contains(label, ignoreCase = true) }

    fun urlContains(fragment: String): Boolean =
        urlBarText?.contains(fragment, ignoreCase = true) == true

    /** The site in the address bar, or null when the bar holds a search rather than a URL. */
    val urlHost: String? get() = BrowserUrlBars.hostOf(urlBarText)

    fun isOnSite(domain: String): Boolean = BrowserUrlBars.hostMatches(urlHost, domain)

    /** Dump used by the in-app debug screen when a rule stops firing and needs new view ids. */
    fun describe(): String = buildString {
        appendLine("package: $packageName")
        appendLine("nodes: $nodeCount" + if (truncated) " (truncated)" else "")
        urlBarText?.let { appendLine("urlBar: $it") }
        appendSection("selected labels", selectedLabels)
        appendSection("visible selected labels", visibleSelectedLabels)
        appendSection("visible content descriptions", visibleContentDescriptions)
        appendSection("visible view ids", visibleViewIds)
        appendSection("view ids", viewIds)
        appendSection("content descriptions", contentDescriptions)
        appendSection("texts", texts)
    }
}

/** Package and view ids only - no screen text. Used to describe where a block was entered from. */
fun ScreenSnapshot.idsOnlySummary(): String = buildString {
    appendLine("package: $packageName")
    appendLine("view ids (${viewIds.size}):")
    viewIds.sorted().forEach { appendLine("  $it") }
}

private fun StringBuilder.appendSection(title: String, values: Set<String>) {
    appendLine()
    appendLine("$title (${values.size}):")
    values.sorted().forEach { appendLine("  $it") }
}
