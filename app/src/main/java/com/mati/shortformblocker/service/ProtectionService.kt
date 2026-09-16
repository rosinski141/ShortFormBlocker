package com.mati.shortformblocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mati.shortformblocker.BlockerApp
import com.mati.shortformblocker.R
import com.mati.shortformblocker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Watchdog. It does not block anything itself - it exists so that:
 *
 * 1. a pending disable actually lands when its cooldown expires, even if the app is never opened;
 * 2. you find out when the accessibility service has been switched off or killed by the system,
 *    which on aggressive OEM builds is the most likely way for this app to quietly stop working.
 */
class ProtectionService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var todayBlocks = 0

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannels()
        scope.launch {
            BlockerApp.from(this@ProtectionService).stats.stats.collect { todayBlocks = it.today }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat(buildStatusNotification(AccessibilityUtils.isServiceEnabled(this)))
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
    }

    private fun refresh() {
        scope.launch { BlockerApp.from(this@ProtectionService).settings.applyDuePending() }

        val enabled = AccessibilityUtils.isServiceEnabled(this)
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.notify(NOTIFICATION_STATUS, buildStatusNotification(enabled))
        if (enabled) {
            notifications.cancel(NOTIFICATION_ALERT)
        } else {
            notifications.notify(NOTIFICATION_ALERT, buildAlertNotification())
        }
    }

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_STATUS, notification, type) }
            .onFailure { Log.w(BlockerAccessibilityService.TAG, "Could not start watchdog service", it) }
    }

    private fun buildStatusNotification(serviceEnabled: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(if (serviceEnabled) "Protection active" else "Protection is OFF")
            .setContentText(
                if (serviceEnabled) {
                    if (todayBlocks == 0) "No short form today" else "$todayBlocks blocks today"
                } else {
                    "The accessibility service is not running"
                },
            )
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun buildAlertNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("ShortFormBlocker is not running")
            .setContentText("Tap to switch the accessibility service back on.")
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    1,
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Protection status", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "The quiet ongoing notification showing protection is alive." },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, "Protection alerts", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Tells you when blocking has stopped working." },
        )
    }

    companion object {
        private const val CHANNEL_STATUS = "protection_status"
        private const val CHANNEL_ALERT = "protection_alert"
        private const val NOTIFICATION_STATUS = 1
        private const val NOTIFICATION_ALERT = 2
        private const val CHECK_INTERVAL_MS = 60_000L

        fun start(context: Context) {
            val intent = Intent(context, ProtectionService::class.java)
            runCatching { context.startForegroundService(intent) }
                .onFailure { Log.w(BlockerAccessibilityService.TAG, "Watchdog start refused", it) }
        }
    }
}
