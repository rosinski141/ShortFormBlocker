package com.mati.shortformblocker.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

object AccessibilityUtils {

    fun isServiceEnabled(context: Context): Boolean {
        val component = ComponentName(context.applicationContext, BlockerAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { entry ->
            entry.equals(component.flattenToString(), ignoreCase = true) ||
                entry.equals(component.flattenToShortString(), ignoreCase = true)
        }
    }

    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
