package com.mati.shortformblocker.detect

/**
 * Browser support: short-form sites opened in a browser are caught by reading the address bar,
 * which is the one part of a web page an accessibility service can read reliably.
 */
object BrowserUrlBars {
    val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "org.mozilla.firefox",
        "org.mozilla.fenix",
        "com.sec.android.app.sbrowser",
        "com.vivaldi.browser",
    )

    /** Address-bar view id suffixes across the browsers above. */
    val URL_BAR_ID_SUFFIXES = listOf(
        "id/url_bar",
        "id/url_field",
        "id/omnibox_text_input",
        "id/mozac_browser_toolbar_url_view",
        "id/location_bar_edit_text",
        "id/toolbar_text",
        "id/search_bar_text",
    )

    /**
     * Path fragments for blocking *part* of a site. Bare domains do not belong here - they go
     * through host matching instead, so that `tiktok.com` cannot match `nottiktok.com`.
     */
    val SHORT_FORM_URL_FRAGMENTS = listOf(
        "youtube.com/shorts",
        "youtu.be/shorts",
        "instagram.com/reel",
        "facebook.com/reel",
        "snapchat.com/spotlight",
    )

    fun isUrlBar(viewId: String): Boolean = URL_BAR_ID_SUFFIXES.any { viewId.endsWith(it) }

    /**
     * The host out of whatever the address bar is showing, which may be a full URL, a bare domain,
     * or - when the bar is focused - something that is not a URL at all.
     *
     * Blocking a whole site has to match on the host and not on a substring of the URL: `x.com`
     * appears inside `netflix.com`, `citrix.com` and `linux.com`.
     */
    fun hostOf(urlBarText: String?): String? {
        var candidate = urlBarText?.trim()?.lowercase() ?: return null
        if (candidate.isEmpty() || candidate.contains(' ')) return null
        val scheme = candidate.indexOf("://")
        if (scheme >= 0) candidate = candidate.substring(scheme + 3)
        candidate = candidate.substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .removePrefix("www.")
        if (!candidate.contains('.')) return null
        return candidate.takeIf { it.isNotEmpty() }
    }

    /** True for the domain itself and any subdomain of it, and nothing else. */
    fun hostMatches(host: String?, domain: String): Boolean {
        if (host == null) return false
        return host == domain || host.endsWith(".$domain")
    }
}
