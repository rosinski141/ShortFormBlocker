package com.mati.shortformblocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shown until the accessibility service is switched on - without it the app can see nothing and
 * block nothing, so it is the only thing that matters on first run.
 */
@Composable
fun SetupCard(
    restrictedSettingsMayApply: Boolean,
    onEnableService: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "One more step",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Turn on the ShortFormBlocker accessibility service. It is how the app sees " +
                    "that a Reels or Shorts feed just opened. Everything stays on the phone - no " +
                    "screen content is stored or sent anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = onEnableService, modifier = Modifier.fillMaxWidth()) {
                Text("Open accessibility settings")
            }
            if (restrictedSettingsMayApply) {
                Text(
                    text = "Greyed out, or refused with \"App was denied access\"? Android does " +
                        "that to every app installed from a browser rather than from a store. " +
                        "Open App info, tap the three dots at the top right, choose \"Allow " +
                        "restricted settings\", then switch the service on again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                TextButton(onClick = onOpenAppInfo) {
                    Text("Open App info")
                }
            }
            Text(
                text = "Then, so Android does not quietly kill it:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = onRequestNotificationPermission) {
                Text("Allow notifications")
            }
            TextButton(onClick = onOpenBatterySettings) {
                Text("Exclude from battery optimisation")
            }
            Text(
                text = "On Xiaomi, Samsung, OnePlus and similar, also allow autostart for this app " +
                    "in the system settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
