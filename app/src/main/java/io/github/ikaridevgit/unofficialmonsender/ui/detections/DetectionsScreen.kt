package io.github.ikaridevgit.unofficialmonsender.ui.detections

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.ikaridevgit.unofficialmonsender.ui.pause.PauseSheet
import io.github.ikaridevgit.unofficialmonsender.ui.pause.DetectionOnCard
import io.github.ikaridevgit.unofficialmonsender.ui.pause.PausedCard
import io.github.ikaridevgit.unofficialmonsender.ui.pause.rememberNow
import androidx.compose.ui.unit.DpOffset
import androidx.core.net.toUri
import io.github.ikaridevgit.unofficialmonsender.ui.components.linkOriginLabel
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonColors
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.data.monloader.JobSnapshot
import io.github.ikaridevgit.unofficialmonsender.data.monloader.JobStatus
import io.github.ikaridevgit.unofficialmonsender.detection.Delivery
import io.github.ikaridevgit.unofficialmonsender.detection.DetectionEntry
import io.github.ikaridevgit.unofficialmonsender.overlay.sendFailureMessage
import io.github.ikaridevgit.unofficialmonsender.ui.components.ButtonRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.GroupDivider
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonBrandTitle
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonButton
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonLink
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonTopBar
import io.github.ikaridevgit.unofficialmonsender.ui.components.PeekingMascot
import io.github.ikaridevgit.unofficialmonsender.ui.components.SectionHeader
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsGroup
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.StatusDot
import io.github.ikaridevgit.unofficialmonsender.ui.settings.SettingsViewModel
import io.github.ikaridevgit.unofficialmonsender.ui.settings.openAccessibilitySettings
import io.github.ikaridevgit.unofficialmonsender.ui.theme.StatusColors
import io.github.ikaridevgit.unofficialmonsender.util.appLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionsScreen(viewModel: SettingsViewModel) {
    val entries by viewModel.detectionLog.collectAsStateWithLifecycle()
    val serviceRunning by viewModel.detectionRunning.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val now = rememberNow()
    val pause = settings?.activePause(now)
    var pauseDialog by rememberSaveable { mutableStateOf(false) }
    if (pauseDialog) {
        PauseSheet(
            active = pause,
            onPause = viewModel::setPause,
            onResume = { viewModel.setPause(null) },
            onDismiss = { pauseDialog = false },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { MonTopBar(title = { MonBrandTitle() }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = padding,
        ) {
            if (pause != null) {
                item(key = "paused") {
                    Column(Modifier.animateItem()) {
                        Spacer(Modifier.height(16.dp))
                        PausedCard(
                            pause = pause,
                            now = now,
                            onChange = { pauseDialog = true },
                            onResume = { viewModel.setPause(null) },
                        )
                    }
                }
            }
            if (!serviceRunning) {
                item { Spacer(Modifier.height(16.dp)) }
                item { DetectionOffCard() }
            } else if (pause == null) {
                item(key = "running") {
                    Column(Modifier.animateItem()) {
                        Spacer(Modifier.height(16.dp))
                        DetectionOnCard(configured = settings?.isConfigured != false, onOpenPause = { pauseDialog = true })
                    }
                }
            }
            item {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(1f),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            SectionHeader(stringResource(R.string.section_latest))
                            if (entries.isNotEmpty()) {
                                MonLink(
                                    text = stringResource(R.string.action_clear),
                                    onClick = viewModel::clearLog,
                                    color = MonColors.fgDim,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                            }
                        }
                        PeekingMascot(Modifier.padding(end = 28.dp))
                    }
                    SettingsGroup {
                        if (settings?.keepLog == false) {
                            SettingsRow(
                                title = stringResource(R.string.log_off),
                                subtitle = stringResource(R.string.log_off_detail),
                            )
                        } else if (entries.isEmpty()) {
                            SettingsRow(
                                title = stringResource(R.string.recent_empty),
                                subtitle = stringResource(R.string.recent_empty_detail),
                            )
                        }
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) GroupDivider()
                            key(entry.id) { EntryRow(entry, onResend = viewModel::resend, onCopy = viewModel::copiedByApp) }
                        }
                    }
                }
            }
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
            leading = { StatusDot(MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        ButtonRow {
            MonButton(stringResource(R.string.action_turn_on), onClick = context::openAccessibilitySettings, accent = true)
        }
    }
}

/** One line of the log, laid out like a row of monloader's queue. Tap it for send again, copy and open. */
@Composable
private fun EntryRow(entry: DetectionEntry, onResend: (DetectionEntry) -> Unit, onCopy: (String) -> Unit) {
    val context = LocalContext.current
    val app = remember(entry.sourcePackage) { entry.sourcePackage?.let { context.appLabel(it) ?: it } }
    val delivery = entry.delivery
    val color = statusColor(entry)
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = entry.url != null) { menu = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(color)
            Text(
                text = statusText(entry),
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            )
            Text(
                text = remember(entry.timeMillis) { timestamp(context, entry.timeMillis) },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.padding(start = 18.dp)) {
            kaomoji(entry)?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                    maxLines = 1,
                    modifier = Modifier.semantics { hideFromAccessibility() },
                )
            }
            if (entry.url != null) {
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            val origin = linkOriginLabel(entry.origin, app)
            if (origin != null) {
                Text(
                    text = origin,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
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
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        val url = entry.url
        if (url != null) {
            EntryMenu(
                expanded = menu,
                canResend = delivery != Delivery.Sending,
                onDismiss = { menu = false },
                onResend = { onResend(entry) },
                onCopy = {
                    onCopy(url)
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText(url, url))
                },
                onOpen = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                },
            )
        }
    }
}

@Composable
private fun EntryMenu(
    expanded: Boolean,
    canResend: Boolean,
    onDismiss: () -> Unit,
    onResend: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = DpOffset(16.dp, 0.dp),
        shape = MaterialTheme.shapes.small,
        containerColor = MonColors.surface,
        border = BorderStroke(1.dp, MonColors.border),
    ) {
        @Composable
        fun item(label: Int, enabled: Boolean = true, action: () -> Unit) = DropdownMenuItem(
            text = { Text(stringResource(label), style = MaterialTheme.typography.bodyMedium) },
            enabled = enabled,
            onClick = {
                onDismiss()
                action()
            },
        )
        item(R.string.action_send_again, enabled = canResend, action = onResend)
        item(R.string.action_copy_link, action = onCopy)
        item(R.string.action_open_link, action = onOpen)
    }
}

/** Time for today's entries, date and time for older ones now that the log survives restarts. */
private fun timestamp(context: Context, millis: Long): String {
    val time = DateFormat.getTimeFormat(context).format(millis)
    return if (DateUtils.isToday(millis)) time else DateFormat.getDateFormat(context).format(millis) + " " + time
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
            JobStatus.CANCELED, JobStatus.REMOVED -> colors.onSurfaceVariant
        }
        is Delivery.Failed -> colors.error
        Delivery.Waiting, Delivery.Sending, Delivery.Dismissed, null -> colors.primary
    }
}

@Composable
private fun statusText(entry: DetectionEntry): String = stringResource(
    when (val delivery = entry.delivery) {
        Delivery.Waiting, Delivery.Sending, Delivery.Dismissed, null -> R.string.status_sending
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
    },
)

/** monloader's queue faces: one for done, one for busy, one for broken. */
private fun kaomoji(entry: DetectionEntry): Int? = when (val delivery = entry.delivery) {
    Delivery.Sending -> R.string.kaomoji_busy
    is Delivery.Failed -> R.string.kaomoji_sad
    is Delivery.Queued -> when (delivery.job?.status) {
        JobStatus.RUNNING -> R.string.kaomoji_busy
        JobStatus.SUCCEEDED -> R.string.kaomoji_happy
        JobStatus.FAILED, JobStatus.INTERRUPTED -> R.string.kaomoji_sad
        else -> null
    }
    else -> null
}

private val JOB_ERRORS =setOf(JobStatus.FAILED, JobStatus.INTERRUPTED, JobStatus.PARTIAL)
