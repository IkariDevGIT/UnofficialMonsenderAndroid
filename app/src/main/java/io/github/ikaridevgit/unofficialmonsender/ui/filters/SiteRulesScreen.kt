package io.github.ikaridevgit.unofficialmonsender.ui.filters

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppSettings
import io.github.ikaridevgit.unofficialmonsender.data.settings.SiteMode
import io.github.ikaridevgit.unofficialmonsender.link.Links
import io.github.ikaridevgit.unofficialmonsender.ui.components.DropdownRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.GroupDivider
import io.github.ikaridevgit.unofficialmonsender.ui.components.SwitchRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonLink
import io.github.ikaridevgit.unofficialmonsender.ui.components.PromptPrefix
import io.github.ikaridevgit.unofficialmonsender.ui.components.SectionHeader
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsGroup
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.monFieldColors
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonColors

@Composable
fun SiteRulesScreen(
    settings: AppSettings,
    suggestions: List<String>,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    FilterScaffold(title = stringResource(R.string.sites_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(
                    text = stringResource(R.string.sites_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item { SectionHeader(stringResource(R.string.sites_default)) }
            item {
                SettingsGroup {
                    DropdownRow(
                        title = stringResource(R.string.setting_site_mode),
                        options = SiteMode.entries.map { stringResource(siteModeLabel(it)) },
                        selected = settings.siteMode.ordinal,
                        onSelect = { index -> onUpdate { it.copy(siteMode = SiteMode.entries[index]) } },
                        detail = stringResource(R.string.setting_site_mode_detail),
                    )
                    GroupDivider()
                    SwitchRow(
                        title = stringResource(R.string.setting_allow_images),
                        subtitle = stringResource(R.string.setting_allow_images_detail),
                        checked = settings.allowImages,
                        onCheckedChange = { on -> onUpdate { it.copy(allowImages = on) } },
                    )
                }
            }
            item { SectionHeader(stringResource(R.string.sites_always)) }
            item {
                HostList(
                    hosts = settings.allowedHosts,
                    suggestions = suggestions,
                    emptyText = stringResource(R.string.sites_always_empty),
                    onAdd = { host -> onUpdate { it.copy(allowedHosts = it.allowedHosts + host, blockedHosts = it.blockedHosts - host) } },
                    onRemove = { host -> onUpdate { it.copy(allowedHosts = it.allowedHosts - host) } },
                )
            }
            item { SectionHeader(stringResource(R.string.sites_never)) }
            item {
                HostList(
                    hosts = settings.blockedHosts,
                    suggestions = suggestions,
                    emptyText = stringResource(R.string.sites_never_empty),
                    onAdd = { host -> onUpdate { it.copy(blockedHosts = it.blockedHosts + host, allowedHosts = it.allowedHosts - host) } },
                    onRemove = { host -> onUpdate { it.copy(blockedHosts = it.blockedHosts - host) } },
                )
            }
        }
    }
}

fun siteModeLabel(mode: SiteMode): Int = when (mode) {
    SiteMode.SUPPORTED -> R.string.site_mode_supported
    SiteMode.ALL -> R.string.site_mode_all
    SiteMode.NONE -> R.string.site_mode_none
}

@Composable
private fun HostList(
    hosts: Set<String>,
    suggestions: List<String>,
    emptyText: String,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }

    fun add() {
        val host = Links.normalizeHostRule(input)
        if (host == null) {
            invalid = true
        } else {
            onAdd(host)
            input = ""
        }
    }

    val removeTemplate = stringResource(R.string.action_remove_host)
    fun removeLabel(host: String) = removeTemplate.format(host)

    SettingsGroup {
        OutlinedTextField(
            value = input,
            onValueChange = {
                input = it
                invalid = false
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
            placeholder = { Text(stringResource(R.string.sites_input_hint)) },
            prefix = { PromptPrefix() },
            colors = monFieldColors(),
            singleLine = true,
            isError = invalid,
            supportingText = if (invalid) {
                { Text(stringResource(R.string.sites_input_invalid)) }
            } else {
                null
            },
            trailingIcon = {
                IconButton(onClick = ::add, enabled = input.isNotBlank()) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_add))
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) add() }),
        )
        val query = input.trim().lowercase().removePrefix("www.")
        val matches = if (query.isEmpty()) emptyList() else suggestions.filter { query in it && it !in hosts }.take(MAX_SUGGESTIONS)
        if (matches.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                matches.forEach { domain ->
                    Text(
                        text = "+ $domain",
                        style = MaterialTheme.typography.labelLarge,
                        color = MonColors.link,
                        modifier = Modifier
                            .background(MonColors.input)
                            .border(1.dp, MonColors.border)
                            .clickable(role = Role.Button) {
                                onAdd(domain)
                                input = ""
                                invalid = false
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }
        }
        GroupDivider()
        if (hosts.isEmpty()) {
            SettingsRow(title = emptyText)
        }
        hosts.sorted().forEachIndexed { index, host ->
            if (index > 0) GroupDivider()
            SettingsRow(
                title = host,
                trailing = {
                    MonLink(
                        text = "×",
                        onClick = { onRemove(host) },
                        color = MonColors.error,
                        modifier = Modifier.semantics { contentDescription = removeLabel(host) },
                    )
                },
            )
        }
    }
}

private const val MAX_SUGGESTIONS = 6
