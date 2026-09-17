package com.mati.shortformblocker.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Android 13+ "restricted settings".
 *
 * An app installed from a browser or a file manager cannot be granted accessibility access the
 * normal way: the switch is greyed out, or flipping it fails with *"App was denied access"* /
 * *"Controlled by restricted setting"*. Nothing on that screen says why, or what to do.
 *
 * The unlock is behind the overflow menu of the app's own info page - **App info -> tap the three
 * dots -> Allow restricted settings** - which nobody finds unaided. For this app the dead end is
 * total: the accessibility service is the only thing that can see a Reels feed open, so an install
 * stuck here does nothing at all.
 *
 * `adb install` does not trip the restriction and opening a downloaded APK does, so this never
 * shows up in development and always shows up for someone installing from the release page.
 */
object RestrictedSettings {

    private const val PLAY_STORE = "com.android.vending"

    /** True when this install is likely subject to the restriction. */
    fun mayApply(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isSideloaded(context)

    /** The app's own info page - where the menu with "Allow restricted settings" lives. */
    fun openAppInfo(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun isSideloaded(context: Context): Boolean {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { pm.getInstallSourceInfo(context.packageName).installingPackageName }
                .getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { pm.getInstallerPackageName(context.packageName) }.getOrNull()
        }
        // null covers adb and plain file-manager installs. Anything that is not Play may be
        // restricted, and showing the hint when it was not needed costs a line of text, while
        // withholding it leaves someone on a screen with no way forward.
        return installer != PLAY_STORE
    }
}
