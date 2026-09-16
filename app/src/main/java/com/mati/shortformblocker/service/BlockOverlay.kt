package com.mati.shortformblocker.service

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.mati.shortformblocker.R

/**
 * The full-screen block card.
 *
 * It is a `TYPE_ACCESSIBILITY_OVERLAY` window rather than an Activity: overlays of that type can be
 * shown by an accessibility service without the SYSTEM_ALERT_WINDOW permission and without tripping
 * the background-activity-launch restrictions that would silently swallow a `startActivity` call.
 *
 * The overlay swallows touches so the feed underneath cannot be swiped while we navigate away, but
 * it is not focusable, so Back and Home still work.
 */
class BlockOverlay(private val service: AccessibilityService) {

    private val windowManager: WindowManager =
        service.getSystemService(WindowManager::class.java)

    private var view: View? = null

    val isShowing: Boolean get() = view != null

    fun show(title: String, subtitle: String, footer: String) {
        val existing = view
        if (existing != null) {
            existing.bind(title, subtitle, footer)
            return
        }
        val inflated = LayoutInflater.from(service).inflate(R.layout.overlay_block, null)
        inflated.bind(title, subtitle, footer)
        inflated.setOnTouchListener { _, _ -> true }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windowManager.addView(inflated, params) }
            .onSuccess { view = inflated }
    }

    fun hide() {
        val current = view ?: return
        view = null
        runCatching { windowManager.removeViewImmediate(current) }
    }

    private fun View.bind(title: String, subtitle: String, footer: String) {
        findViewById<TextView>(R.id.overlay_title).text = title
        findViewById<TextView>(R.id.overlay_subtitle).text = subtitle
        findViewById<TextView>(R.id.overlay_footer).text = footer
    }
}
