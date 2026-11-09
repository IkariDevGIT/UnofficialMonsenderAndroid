package io.github.unofficialmonsender.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.data.settings.AppFilterMode
import io.github.unofficialmonsender.data.settings.AppSettings
import io.github.unofficialmonsender.ui.components.GroupDivider
import io.github.unofficialmonsender.ui.components.NavigationRow
import io.github.unofficialmonsender.ui.components.SettingsGroup
import io.github.unofficialmonsender.ui.components.SettingsRow
import io.github.unofficialmonsender.ui.components.StatusDot
import io.github.unofficialmonsender.ui.components.SwitchRow
import io.github.unofficialmonsender.ui.theme.StatusColors

@Composable
fun DetectionSection(
    settings: AppSettings,
    serviceRunning: Boolean,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onOpenApps: () -> Unit,
    onOpenSites: () -> Unit,
    onMessage: (Int) -> Unit,
) {
    val context = LocalContext.current
    SettingsGroup {
        SettingsRow(
            title = stringResource(if (serviceRunning) R.string.detection_on else R.string.detection_off),
            subtitle = stringResource(if (serviceRunning) R.string.detection_on_detail else R.string.detection_off_detail),
            leading = { StatusDot(if (serviceRunning) StatusColors.success else MaterialTheme.colorScheme.outline) },
        )
        if (!serviceRunning) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 20.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (Build.VERSION.SDK_INT >= 33) {
                    TextButton(onClick = context::openAppInfo) { Text(stringResource(R.string.action_app_info)) }
                }
                Button(onClick = context::openAccessibilitySettings) { Text(stringResource(R.string.action_turn_on)) }
            }
            Column(
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (Build.VERSION.SDK_INT >= 33) HintText(stringResource(R.string.detection_restricted_hint))
                TroubleshootingHint()
            }
        }
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
                    TextButton(onClick = {
                        context.requestAddTile { added -> if (added) onMessage(R.string.message_tile_added) }
                    }) { Text(stringResource(R.string.action_add)) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlaySection(settings: AppSettings, onUpdate: ((AppSettings) -> AppSettings) -> Unit) {
    SettingsGroup {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.setting_auto_dismiss), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.setting_auto_dismiss_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val choices = AppSettings.AUTO_DISMISS_CHOICES
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                choices.forEachIndexed { index, seconds ->
                    SegmentedButton(
                        selected = settings.autoDismissSeconds == seconds,
                        onClick = { onUpdate { it.copy(autoDismissSeconds = seconds) } },
                        shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                    ) {
                        Text(if (seconds == 0) stringResource(R.string.auto_dismiss_off) else stringResource(R.string.auto_dismiss_seconds, seconds))
                    }
                }
            }
        }
        GroupDivider()
        SwitchRow(
            title = stringResource(R.string.setting_haptics),
            checked = settings.haptics,
            onCheckedChange = { on -> onUpdate { it.copy(haptics = on) } },
        )
    }
}

private const val TROUBLESHOOTING_URL = "https://github.com/IkariDevGIT/UnofficialMonsenderAndroid#troubleshooting"
