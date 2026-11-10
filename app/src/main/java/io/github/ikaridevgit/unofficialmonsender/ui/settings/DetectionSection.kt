package io.github.ikaridevgit.unofficialmonsender.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.ikaridevgit.unofficialmonsender.data.settings.Pause
import io.github.ikaridevgit.unofficialmonsender.ui.pause.PauseDialog
import io.github.ikaridevgit.unofficialmonsender.ui.pause.pauseStatus
import io.github.ikaridevgit.unofficialmonsender.ui.pause.rememberNow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppFilterMode
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppSettings
import io.github.ikaridevgit.unofficialmonsender.ui.components.ButtonRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.GroupDivider
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonButton
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonChoiceRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.NavigationRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsGroup
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.StatusDot
import io.github.ikaridevgit.unofficialmonsender.ui.components.SwitchRow
import io.github.ikaridevgit.unofficialmonsender.ui.theme.StatusColors

@Composable
fun DetectionSection(
    settings: AppSettings,
    serviceRunning: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSites: () -> Unit,
    onMessage: (Int) -> Unit,
    onSetPause: (Pause?) -> Unit,
) {
    val context = LocalContext.current
    val now = rememberNow()
    val pause = settings.activePause(now)
    var pauseDialog by rememberSaveable { mutableStateOf(false) }
    if (pauseDialog) {
        PauseDialog(
            active = pause,
            onPause = onSetPause,
            onResume = { onSetPause(null) },
            onDismiss = { pauseDialog = false },
        )
    }
    SettingsGroup {
        SettingsRow(
            title = stringResource(if (serviceRunning) R.string.detection_on else R.string.detection_off),
            subtitle = stringResource(if (serviceRunning) R.string.detection_on_detail else R.string.detection_off_detail),
            leading = { StatusDot(if (serviceRunning) StatusColors.success else MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        if (!serviceRunning) {
            ButtonRow {
                if (Build.VERSION.SDK_INT >= 33) {
                    MonButton(stringResource(R.string.action_app_info), onClick = context::openAppInfo)
                }
                MonButton(stringResource(R.string.action_turn_on), onClick = context::openAccessibilitySettings, accent = true)
            }
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (Build.VERSION.SDK_INT >= 33) HintText(stringResource(R.string.detection_restricted_hint))
                TroubleshootingHint()
            }
        }
        GroupDivider()
        SettingsRow(
            title = stringResource(if (pause != null) R.string.paused_title else R.string.pause_row_title),
            subtitle = if (pause != null) pauseStatus(pause, now) else stringResource(R.string.pause_row_detail),
            leading = if (pause != null) {
                { StatusDot(StatusColors.warning) }
            } else {
                null
            },
            trailing = {
                MonButton(
                    text = stringResource(if (pause != null) R.string.action_change else R.string.action_pause),
                    onClick = { pauseDialog = true },
                )
            },
        )
        GroupDivider()
        SwitchRow(
            title = stringResource(R.string.setting_show_overlay),
            subtitle = stringResource(R.string.setting_show_overlay_detail),
            checked = settings.detectionEnabled,
            onCheckedChange = { on -> onUpdate { it.copy(detectionEnabled = on) } },
        )
        GroupDivider()
        SwitchRow(
            title = stringResource(R.string.setting_supported_only),
            subtitle = stringResource(R.string.setting_supported_only_detail),
            checked = settings.supportedSitesOnly,
            onCheckedChange = { on -> onUpdate { it.copy(supportedSitesOnly = on) } },
        )
        GroupDivider()
        NavigationRow(
            title = stringResource(R.string.apps_title),
            subtitle = appFilterSummary(settings),
            onClick = onOpenApps,
        )
        GroupDivider()
        NavigationRow(
            title = stringResource(R.string.sites_title),
            subtitle = siteRulesSummary(settings),
            onClick = onOpenSites,
        )
        GroupDivider()
        SettingsRow(
            title = stringResource(R.string.tile_title),
            subtitle = stringResource(R.string.tile_detail),
            trailing = if (Build.VERSION.SDK_INT >= 33) {
                {
                    MonButton(stringResource(R.string.action_add), onClick = {
                        context.requestAddTile { added -> if (added) onMessage(R.string.message_tile_added) }
                    })
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun HintText(text: String) = HintText(AnnotatedString(text))

@Composable
private fun HintText(text: AnnotatedString) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TroubleshootingHint() {
    val prefix = stringResource(R.string.detection_battery_hint)
    val link = stringResource(R.string.detection_battery_link)
    val linkStyle = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    HintText(
        buildAnnotatedString {
            append(prefix)
            append(' ')
            withLink(LinkAnnotation.Url(TROUBLESHOOTING_URL, linkStyle)) { append(link) }
        },
    )
}

@Composable
private fun appFilterSummary(settings: AppSettings): String {
    val count = settings.appFilterPackages.size
    return when (settings.appFilterMode) {
        AppFilterMode.ANY -> stringResource(R.string.apps_summary_any)
        AppFilterMode.ONLY_SELECTED -> pluralStringResource(R.plurals.apps_summary_only, count, count)
        AppFilterMode.EXCEPT_SELECTED -> pluralStringResource(R.plurals.apps_summary_except, count, count)
    }
}

@Composable
private fun siteRulesSummary(settings: AppSettings): String {
    val allowed = settings.allowedHosts.size
    val blocked = settings.blockedHosts.size
    return if (allowed == 0 && blocked == 0) {
        stringResource(R.string.sites_summary_none)
    } else {
        stringResource(R.string.sites_summary, allowed, blocked)
    }
}

@Composable
fun OverlaySection(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsGroup {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(stringResource(R.string.setting_auto_dismiss), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.setting_auto_dismiss_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val off = stringResource(R.string.auto_dismiss_off)
            val labels = AppSettings.AUTO_DISMISS_CHOICES.map { seconds ->
                if (seconds == 0) off else stringResource(R.string.auto_dismiss_seconds, seconds)
            }
            MonChoiceRow(
                labels = labels,
                selected = AppSettings.AUTO_DISMISS_CHOICES.indexOf(settings.autoDismissSeconds),
                onSelect = { index -> onUpdate { it.copy(autoDismissSeconds = AppSettings.AUTO_DISMISS_CHOICES[index]) } },
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        GroupDivider()
        SwitchRow(
            title = stringResource(R.string.setting_haptics),
            checked = settings.haptics,
            onCheckedChange = { on -> onUpdate { it.copy(haptics = on) } },
        )
    }
}

@Composable
fun PrivacySection(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsGroup {
        SwitchRow(
            title = stringResource(R.string.setting_no_log),
            subtitle = stringResource(R.string.setting_no_log_detail),
            checked = !settings.keepLog,
            onCheckedChange = { off -> onUpdate { it.copy(keepLog = !off) } },
        )
    }
}

private const val TROUBLESHOOTING_URL = "https://github.com/IkariDevGIT/UnofficialMonsenderAndroid#troubleshooting"
