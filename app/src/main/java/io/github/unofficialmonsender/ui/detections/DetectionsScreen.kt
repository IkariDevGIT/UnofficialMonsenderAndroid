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
import androidx.compose.ui.res.pluralStringResource
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.data.monloader.JobSnapshot
import io.github.unofficialmonsender.data.monloader.JobStatus
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
            val detail = when (delivery) {
                is Delivery.Failed -> sendFailureMessage(delivery.reason, delivery.detail) to true
                is Delivery.Queued -> delivery.job?.let { job -> jobDetail(job)?.let { it to (job.status in JOB_ERRORS) } }
                else -> null
            }
            if (detail != null) {
                Text(
                    text = detail.first,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (detail.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 22.dp, top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun jobDetail(job: JobSnapshot): String? {
    if (job.status in JOB_ERRORS && job.error != null) return job.error
    if (!job.status.isFinished || job.status == JobStatus.REMOVED) return null
    val parts = listOfNotNull(
        job.created.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.job_created, it, it) },
        job.duplicate.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.job_duplicate, it, it) },
        job.skipped.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.job_skipped, it, it) },
        job.failed.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.job_failed, it, it) },
    )
    return parts.joinToString(" · ").ifEmpty { null }
}

@Composable
private fun statusColor(entry: DetectionEntry): Color {
    val colors = MaterialTheme.colorScheme
    return when (val delivery = entry.delivery) {
        is Delivery.Queued -> when (delivery.job?.status) {
            null, JobStatus.QUEUED, JobStatus.RUNNING -> colors.primary
            JobStatus.SUCCEEDED -> StatusColors.success
            JobStatus.PARTIAL -> StatusColors.warning
            JobStatus.FAILED, JobStatus.INTERRUPTED -> colors.error
            JobStatus.CANCELED, JobStatus.REMOVED -> colors.outline
        }
        is Delivery.Failed -> colors.error
        Delivery.Waiting, Delivery.Sending -> colors.primary
        Delivery.Dismissed, null -> colors.outline
    }
}

@Composable
private fun statusText(entry: DetectionEntry): String = stringResource(
    when (val delivery = entry.delivery) {
        Delivery.Waiting -> R.string.status_waiting
        Delivery.Sending -> R.string.status_sending
        is Delivery.Queued -> when (delivery.job?.status) {
            null, JobStatus.QUEUED -> R.string.status_queued
            JobStatus.RUNNING -> R.string.status_running
            JobStatus.SUCCEEDED -> R.string.status_succeeded
            JobStatus.PARTIAL -> R.string.status_partial
            JobStatus.FAILED -> R.string.status_job_failed
            JobStatus.CANCELED -> R.string.status_canceled
            JobStatus.INTERRUPTED -> R.string.status_interrupted
            JobStatus.REMOVED -> R.string.status_removed
        }
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

private val JOB_ERRORS = setOf(JobStatus.FAILED, JobStatus.INTERRUPTED, JobStatus.PARTIAL)
