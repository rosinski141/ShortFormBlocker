package com.mati.shortformblocker.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mati.shortformblocker.BlockerApp
import com.mati.shortformblocker.data.BlockStats
import com.mati.shortformblocker.data.BlockerSettings
import com.mati.shortformblocker.data.PendingDisable
import com.mati.shortformblocker.detect.FeedBudgetHolder
import com.mati.shortformblocker.service.AccessibilityUtils
import com.mati.shortformblocker.service.ProtectionService
import com.mati.shortformblocker.service.RestrictedSettings
import com.mati.shortformblocker.ui.theme.BlockerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlockerTheme {
                BlockerRoot(onRequestNotificationPermission = ::requestNotificationPermission)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private enum class Screen { HOME, DEBUG }

@Composable
private fun BlockerRoot(onRequestNotificationPermission: () -> Unit) {
    val context = LocalContext.current
    val app = remember(context) { BlockerApp.from(context) }
    val scope = rememberCoroutineScope()

    val settings by app.settings.settings.collectAsState(initial = BlockerSettings())
    val stats by app.stats.stats.collectAsState(initial = BlockStats())

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var serviceEnabled by remember { mutableStateOf(AccessibilityUtils.isServiceEnabled(context)) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    // Where the app was installed from cannot change while it runs, so this is read once.
    val restrictedSettingsMayApply = remember(context) { RestrictedSettings.mayApply(context) }

    // One ticker drives the cooldown countdown, the "is the service still on?" check, and landing
    // a pending disable the moment it comes due even if the watchdog has been killed.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            serviceEnabled = AccessibilityUtils.isServiceEnabled(context)
            delay(1_000)
        }
    }
    // The feed budget lives in the accessibility service, not in DataStore - it is per visit, not
    // per setting - so it is read off the holder on the same tick that drives the countdowns.
    val feedStates = remember(now) { FeedBudgetHolder.states }

    LaunchedEffect(settings.pending, now) {
        val pending = settings.pending ?: return@LaunchedEffect
        if (pending.isDue(now)) app.settings.applyDuePending(now)
    }
    LaunchedEffect(serviceEnabled) {
        if (serviceEnabled) ProtectionService.start(context)
    }

    when (screen) {
        Screen.HOME -> HomeScreen(
            settings = settings,
            stats = stats,
            serviceEnabled = serviceEnabled,
            restrictedSettingsMayApply = restrictedSettingsMayApply,
            feedStates = feedStates,
            now = now,
            onEnableService = { AccessibilityUtils.openAccessibilitySettings(context) },
            onOpenAppInfo = { RestrictedSettings.openAppInfo(context) },
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenBatterySettings = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onSetProtection = { enabled ->
                scope.launch {
                    if (enabled) {
                        app.settings.enable(PendingDisable.TARGET_ALL)
                    } else {
                        app.settings.requestDisable(PendingDisable.TARGET_ALL)
                    }
                }
            },
            onSetRule = { ruleId, enabled ->
                scope.launch {
                    if (enabled) app.settings.enable(ruleId) else app.settings.requestDisable(ruleId)
                }
            },
            onSetFeedBudget = { budget -> scope.launch { app.settings.setFeedBudget(budget) } },
            onCancelPending = { scope.launch { app.settings.cancelPending() } },
            onCancelPendingFeedBudget = {
                scope.launch { app.settings.cancelPendingFeedBudget() }
            },
            onOpenDebug = { screen = Screen.DEBUG },
        )

        Screen.DEBUG -> DebugScreen(
            now = now,
            stats = stats,
            onBack = { screen = Screen.HOME },
        )
    }
}
