package com.mati.shortformblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * The accessibility service is restarted by the system on its own after a reboot or an update; this
 * only brings the watchdog back with it.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> ProtectionService.start(context)
        }
    }
}
