package com.mati.shortformblocker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mati.shortformblocker.data.BlockStats
import com.mati.shortformblocker.detect.SnapshotHolder
import com.mati.shortformblocker.service.BlockerAccessibilityService

/**
 * Maintenance tool. Open the app that stopped being blocked, come back here, and the last screen
 * the service saw is dumped with every view id on it - the raw material for a new rule signal.
 */
@Composable
fun DebugScreen(now: Long, stats: BlockStats, onBack: () -> Unit) {
    val context = LocalContext.current
    val service = BlockerAccessibilityService.instance
    var captureAll by remember { mutableStateOf(service?.captureAllPackages ?: false) }

    // `now` ticks every second, so reading the holder here keeps the dump fresh.
    val snapshot = remember(now) { SnapshotHolder.last }
    val matchedRuleId = remember(now) { SnapshotHolder.lastMatchedRuleId }
    val policyNote = remember(now) { SnapshotHolder.lastPolicyNote }
    val capturedAt = remember(now) { SnapshotHolder.lastCapturedAt }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(
                text = "Last screen seen",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Capture every app", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Normally only apps with a rule are watched. Turn this on to " +
                                "capture view ids from any app, then turn it back off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = captureAll,
                        enabled = service != null,
                        onCheckedChange = {
                            captureAll = it
                            service?.setCaptureAllPackages(it)
                        },
                    )
                }
            }

            if (service == null) {
                Text(
                    text = "The accessibility service is not running, so there is nothing to show.",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            val evidence = stats.lastBlockDump
            if (evidence != null) {
                Text(
                    text = "Why the last block fired",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "rule: ${stats.lastBlockRuleId ?: "unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                OutlinedButton(onClick = { copyToClipboard(context, evidence) }) {
                    Text("Copy block evidence")
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = evidence,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            if (snapshot != null) {
                Text(
                    text = buildString {
                        append("matched rule: ")
                        append(matchedRuleId ?: "none")
                        if (capturedAt > 0) {
                            append("  (")
                            append(formatCountdown(now - capturedAt))
                            append(" ago)")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (policyNote != null) {
                    Text(
                        text = policyNote,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = { copyToClipboard(context, snapshot.describe()) }) {
                    Text("Copy dump")
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = snapshot.describe(),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            } else {
                Text("No screen captured yet. Open one of the blocked apps, then come back.")
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("ShortFormBlocker snapshot", text))
}
