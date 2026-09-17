package com.mati.shortformblocker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mati.shortformblocker.data.BlockStats
import com.mati.shortformblocker.data.BlockerSettings
import com.mati.shortformblocker.data.PendingDisable
import com.mati.shortformblocker.detect.BlockMode
import com.mati.shortformblocker.detect.BlockRule
import com.mati.shortformblocker.detect.FeedBudget
import com.mati.shortformblocker.detect.FeedVisitState
import com.mati.shortformblocker.detect.RuleCatalog
import com.mati.shortformblocker.detect.feedStateFor

@Composable
fun HomeScreen(
    settings: BlockerSettings,
    stats: BlockStats,
    serviceEnabled: Boolean,
    restrictedSettingsMayApply: Boolean,
    feedStates: Map<String, FeedVisitState>,
    now: Long,
    onEnableService: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSetProtection: (Boolean) -> Unit,
    onSetRule: (String, Boolean) -> Unit,
    onSetFeedBudget: (FeedBudget) -> Unit,
    onCancelPending: () -> Unit,
    onCancelPendingFeedBudget: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "ShortFormBlocker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )

            if (!serviceEnabled) {
                SetupCard(
                    restrictedSettingsMayApply = restrictedSettingsMayApply,
                    onEnableService = onEnableService,
                    onOpenAppInfo = onOpenAppInfo,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenBatterySettings = onOpenBatterySettings,
                )
            }

            StatusCard(
                settings = settings,
                serviceEnabled = serviceEnabled,
                now = now,
                onSetProtection = onSetProtection,
                onCancelPending = onCancelPending,
            )

            StatsCard(stats = stats)

            Text(
                text = "What gets blocked",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            RuleCatalog.ALL.forEach { rule ->
                RuleCard(
                    rule = rule,
                    enabled = settings.isRuleOn(rule.id, now),
                    pending = settings.pendingFor(rule.id),
                    blocks = stats.perRule[rule.id] ?: 0,
                    now = now,
                    onSetRule = onSetRule,
                    onCancelPending = onCancelPending,
                )
            }

            FeedBudgetCard(
                settings = settings,
                feedStates = feedStates,
                now = now,
                onSetFeedBudget = onSetFeedBudget,
                onCancelPending = onCancelPendingFeedBudget,
            )

            TextButton(onClick = onOpenDebug) {
                Text("Debug: inspect the last screen")
            }
            Text(
                text = "Apps rename their view ids without warning. If something stops being " +
                    "blocked, open it, come back here and use the debug screen to grab the new ids.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(
    settings: BlockerSettings,
    serviceEnabled: Boolean,
    now: Long,
    onSetProtection: (Boolean) -> Unit,
    onCancelPending: () -> Unit,
) {
    val protectionOn = settings.isProtectionOn(now)
    val pending = settings.pendingFor(PendingDisable.TARGET_ALL)
    val live = protectionOn && serviceEnabled

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (live) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            live -> "Protection active"
                            !serviceEnabled -> "Accessibility service off"
                            else -> "Protection off"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (live) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                    Text(
                        text = if (live) {
                            "Short form gets closed the moment it opens."
                        } else {
                            "Nothing is being blocked right now."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (live) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }
                Switch(checked = protectionOn, onCheckedChange = onSetProtection)
            }

            if (pending != null) {
                CooldownNotice(
                    remainingMillis = pending.remainingMillis(now),
                    label = "Protection switches off in",
                    onCancelPending = onCancelPending,
                    onPrimary = live,
                )
            }
        }
    }
}

@Composable
private fun StatsCard(stats: BlockStats) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            StatColumn(value = stats.today, label = "today")
            StatColumn(value = stats.last7Days, label = "last 7 days")
            StatColumn(value = stats.allTime, label = "all time")
        }
    }
}

@Composable
private fun StatColumn(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The one knob in the app. Tightening it - fewer screens, or a longer wait for a refill - takes
 * effect as you tap; loosening it waits out the same cooldown as switching a rule off, because
 * "just five more screens" at the moment the feed closes is exactly the decision this app exists to
 * take away from you.
 */
@Composable
private fun FeedBudgetCard(
    settings: BlockerSettings,
    feedStates: Map<String, FeedVisitState>,
    now: Long,
    onSetFeedBudget: (FeedBudget) -> Unit,
    onCancelPending: () -> Unit,
) {
    val budget = settings.feedBudgetAt(now)
    val pending = settings.pendingFeedBudget?.takeIf { !it.isDue(now) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Feed budget",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "How much feed the Facebook and Instagram rules let past per visit, and " +
                    "how long you have to stay out of the app before it refills.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Stepper(
                label = "Screens per visit",
                value = "${budget.screens}",
                onLess = { onSetFeedBudget(budget.copy(screens = budget.screens - 1)) },
                onMore = { onSetFeedBudget(budget.copy(screens = budget.screens + 1)) },
            )
            Stepper(
                label = "Refills after",
                value = "${budget.resetMinutes} min away",
                onLess = { onSetFeedBudget(budget.copy(resetMinutes = budget.resetMinutes - 5)) },
                onMore = { onSetFeedBudget(budget.copy(resetMinutes = budget.resetMinutes + 5)) },
            )
            FeedLockoutSection(
                settings = settings,
                feedStates = feedStates,
                now = now,
            )

            if (pending != null) {
                Text(
                    text = "Waiting: ${pending.budget.screens} screens, refills after " +
                        "${pending.budget.resetMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                CooldownNotice(
                    remainingMillis = pending.remainingMillis(now),
                    label = "More feed in",
                    onCancelPending = onCancelPending,
                    onPrimary = false,
                )
            }
        }
    }
}

/** One feed's row: the rule, whether it is being enforced, and the visit if there is one. */
private data class FeedLockoutLine(
    val rule: BlockRule,
    val enabled: Boolean,
    val state: FeedVisitState?,
)

/**
 * Where each budgeted feed stands right now: full, part spent, or closed with a countdown.
 *
 * The countdown answers the one question the block card cannot: "how long until I can read the feed
 * again?" It runs from the last scroll of the feed, so it keeps ticking while you are in the app
 * answering a message - the small print says so, because a timer whose rules you cannot see is one
 * you end up testing by opening the app to check.
 */
@Composable
private fun FeedLockoutSection(
    settings: BlockerSettings,
    feedStates: Map<String, FeedVisitState>,
    now: Long,
) {
    val protectionOn = settings.isProtectionOn(now)
    val lines = RuleCatalog.ALL
        .filter { it.mode == BlockMode.BUDGETED_FEED }
        .map { rule ->
            val enabled = protectionOn && settings.isRuleOn(rule.id, now)
            FeedLockoutLine(
                rule = rule,
                enabled = enabled,
                state = feedStates.feedStateFor(rule.packages).takeIf { enabled },
            )
        }
    if (lines.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { (rule, enabled, state) ->
            val lockedOut = state != null && state.isLockedOut(now)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = rule.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = when {
                        !enabled -> "not blocking"
                        state == null || state.isStale(now) -> "full budget"
                        lockedOut -> "closed"
                        else -> "%.1f of %d screens".format(
                            state.screensScrolled,
                            state.screensAllowed,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (lockedOut) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (lockedOut) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (lockedOut) {
                Text(
                    text = "Refills in ${formatCountdown(state.refillRemainingMillis(now))}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (lines.any { it.state != null && it.state.isLockedOut(now) }) {
            Text(
                text = "Counted from your last scroll of the feed, so reading your messages " +
                    "does not hold it up. Scrolling the feed again starts the wait over.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onLess: () -> Unit,
    onMore: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onLess) { Text("-") }
        Text(
            text = value,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        OutlinedButton(onClick = onMore) { Text("+") }
    }
}

@Composable
private fun RuleCard(
    rule: BlockRule,
    enabled: Boolean,
    pending: PendingDisable?,
    blocks: Int,
    now: Long,
    onSetRule: (String, Boolean) -> Unit,
    onCancelPending: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = rule.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = rule.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (blocks > 0) {
                        Text(
                            text = "$blocks blocks so far",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { onSetRule(rule.id, it) },
                )
            }
            if (pending != null) {
                CooldownNotice(
                    remainingMillis = pending.remainingMillis(now),
                    label = "Switches off in",
                    onCancelPending = onCancelPending,
                    onPrimary = false,
                )
            }
        }
    }
}

@Composable
private fun CooldownNotice(
    remainingMillis: Long,
    label: String,
    onCancelPending: () -> Unit,
    onPrimary: Boolean,
) {
    val contentColor = if (onPrimary) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$label ${formatCountdown(remainingMillis)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
        )
        Text(
            text = "You asked for this delay. Plenty of time to change your mind.",
            style = MaterialTheme.typography.bodySmall,
            color = contentColor,
        )
        OutlinedButton(
            onClick = onCancelPending,
            // The notice sits on a filled card, so the button has to borrow that card's content
            // colour or it renders green on green.
            colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
            border = BorderStroke(1.dp, contentColor),
        ) {
            Text("Cancel, keep blocking")
        }
    }
}

fun formatCountdown(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
