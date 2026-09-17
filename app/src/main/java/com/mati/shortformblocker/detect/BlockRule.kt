package com.mati.shortformblocker.detect

enum class BlockMode {
    /** The whole app is short form (TikTok); matching the package is enough. */
    WHOLE_APP,

    /** Only some surfaces are short form; a signal must be present on screen. */
    SIGNAL,

    /**
     * A feed that is fine in small doses and endless by design - the home feed of a social app.
     * The signals identify the surface as usual, but a match is only enforced once the scroll
     * budget for this visit has been spent; [com.mati.shortformblocker.detect.FeedBudgetPolicy]
     * owns that decision.
     */
    BUDGETED_FEED,
}

/**
 * One blockable surface. Signals are deliberately redundant: apps rename view ids without notice,
 * so any single signal firing is enough. A rule never matches on package name alone unless it is
 * [BlockMode.WHOLE_APP].
 */
data class BlockRule(
    val id: String,
    val displayName: String,
    val description: String,
    val packages: Set<String>,
    val mode: BlockMode = BlockMode.SIGNAL,
    /** Substrings matched against `viewIdResourceName` of any node on screen. */
    val viewIdContains: List<String> = emptyList(),
    /** Substrings matched against labels of *selected* nodes only, i.e. the open tab. */
    val selectedLabelContains: List<String> = emptyList(),
    /**
     * Substrings matched against the labels - content descriptions and text - of nodes that are
     * actually on screen. The signal of last resort, for a surface that offers nothing better:
     * Facebook ships with its resource names stripped, so every id it reports is literally
     * `com.facebook.katana:id/(name removed)`, and its full-screen reel player marks no node as
     * selected either. See [FacebookSurfaces] for what is safe to match there.
     *
     * Only ever matched against visible nodes: a label is a much weaker signal than a view id, so
     * an off-screen one is not worth acting on.
     */
    val visibleLabelContains: List<String> = emptyList(),
    /** Substrings matched against the browser address bar, for blocking part of a site. */
    val urlContains: List<String> = emptyList(),
    /**
     * Whole sites to block in a browser, matched against the host so that a domain only matches
     * itself and its subdomains - never a site that merely contains it, like netflix.com for x.com.
     */
    val urlHostEquals: List<String> = emptyList(),
    /**
     * Match view ids and selected labels against nodes that are actually on screen, ignoring the
     * rest of the tree. Needed for apps that keep a blocked surface attached while you are looking
     * at something else - Instagram's swipeable pager holds the Reels page next to the home feed,
     * so without this the home feed carries every reels id and the rule fires on the whole app.
     */
    val visibleSignalsOnly: Boolean = false,
    val enabledByDefault: Boolean = true,
) {
    fun matches(snapshot: ScreenSnapshot): Boolean {
        if (snapshot.packageName !in packages) return false
        if (mode == BlockMode.WHOLE_APP) return true
        val hasViewId = if (visibleSignalsOnly) snapshot::containsVisibleViewIdFragment
        else snapshot::containsViewIdFragment
        val hasSelectedLabel = if (visibleSignalsOnly) snapshot::hasVisibleSelectedLabel
        else snapshot::hasSelectedLabel
        return viewIdContains.any(hasViewId) ||
            selectedLabelContains.any(hasSelectedLabel) ||
            visibleLabelContains.any(snapshot::hasVisibleLabel) ||
            urlContains.any(snapshot::urlContains) ||
            urlHostEquals.any(snapshot::isOnSite)
    }
}

object RuleMatcher {
    /** The first enabled rule that fires for this screen, or null if the screen is fine. */
    fun match(snapshot: ScreenSnapshot, rules: List<BlockRule>): BlockRule? =
        rules.firstOrNull { it.matches(snapshot) }
}
