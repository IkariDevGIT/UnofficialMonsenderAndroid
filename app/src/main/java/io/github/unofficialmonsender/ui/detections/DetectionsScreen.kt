package io.github.unofficialmonsender.ui.detections

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.detection.Delivery
import io.github.unofficialmonsender.detection.DetectionEntry
import io.github.unofficialmonsender.detection.DetectionOutcome
import io.github.unofficialmonsender.overlay.sendFailureMessage
import io.github.unofficialmonsender.ui.components.SettingsGroup
import io.github.unofficialmonsender.ui.components.SettingsRow
import io.github.unofficialmonsender.ui.components.StatusDot
import io.github.unofficialmonsender.ui.settings.SettingsViewModel
import io.github.unofficialmonsender.ui.settings.openAccessibilitySettings
import io.github.unofficialmonsender.ui.theme.StatusColors
import io.github.unofficialmonsender.util.appLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionsScreen(viewModel: SettingsViewModel) {
    val entries by viewModel.detectionLog.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.detectionRunning.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!serviceRunning) item { DetectionOffCard() }
            if (entries.isEmpty()) {
                item {
                    SettingsGroup {
                        SettingsRow(
                            title = stringResource(R.string.recent_empty),
                            subtitle = stringResource(R.string.recent_empty_detail),
                        )
                    }
                }
            }
            items(entries, key = { it.id }) { EntryCard(it) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DetectionOffCard() {
    val context = LocalContext.current
    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.detection_off),
            subtitle = stringResource(R.string.detection_off_detail),
            leading = { StatusDot(MaterialTheme.colorScheme.outline) },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = context::openAccessibilitySettings) { Text(stringResource(R.string.action_turn_on)) }
        }
    }
}

@Composable
private fun EntryCard(entry: DetectionEntry) {
    val context = LocalContext.current
    val timeFormat = remember(context) { DateFormat.getTimeFormat(context) }
    val app = remember(entry.sourcePackage) { entry.sourcePackage?.let { context.appLabel(it) ?: it } }
    val delivery = entry.delivery
    SettingsGroup {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(statusColor(entry))
                Text(
                    text = statusText(entry),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                )
                Text(
                    text = timeFormat.format(entry.timeMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.url != null) {
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 22.dp, top = 6.dp),
                )
            }
            if (app != null) {
                Text(
                    text = stringResource(R.string.overlay_copied_from, app),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 22.dp, top = 2.dp),
                )
            }
            if (delivery is Delivery.Failed) {
                Text(
                    text = sendFailureMessage(delivery.reason, delivery.detail),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 22.dp, top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun statusColor(entry: DetectionEntry): Color = when (entry.delivery) {
    Delivery.Sent -> StatusColors.success
    is Delivery.Failed -> MaterialTheme.colorScheme.error
    Delivery.Waiting, Delivery.Sending -> MaterialTheme.colorScheme.primary
    Delivery.Dismissed, null -> MaterialTheme.colorScheme.outline
}

@Composable
private fun statusText(entry: DetectionEntry): String = stringResource(
    when (entry.delivery) {
        Delivery.Waiting -> R.string.status_waiting
        Delivery.Sending -> R.string.status_sending
        Delivery.Sent -> R.string.status_sent
        is Delivery.Failed -> R.string.status_failed
        Delivery.Dismissed -> R.string.status_dismissed
        null -> when (entry.outcome) {
            DetectionOutcome.SHOWN -> R.string.status_waiting
            DetectionOutcome.NO_LINK -> R.string.outcome_no_link
            DetectionOutcome.UNREADABLE -> R.string.outcome_unreadable
            DetectionOutcome.REPEATED -> R.string.outcome_repeated
            DetectionOutcome.NOT_CONFIGURED -> R.string.outcome_not_configured
            DetectionOutcome.APP_FILTERED -> R.string.outcome_app_filtered
            DetectionOutcome.SITE_BLOCKED -> R.string.outcome_site_blocked
            DetectionOutcome.UNSUPPORTED -> R.string.outcome_unsupported
        }
    },
)
