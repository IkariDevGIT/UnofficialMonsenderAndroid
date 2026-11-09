package io.github.unofficialmonsender.ui.filters

import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.data.settings.AppSettings
import io.github.unofficialmonsender.link.Links
import io.github.unofficialmonsender.ui.components.GroupDivider
import io.github.unofficialmonsender.ui.components.MonLink
import io.github.unofficialmonsender.ui.components.PromptPrefix
import io.github.unofficialmonsender.ui.components.SectionHeader
import io.github.unofficialmonsender.ui.components.SettingsGroup
import io.github.unofficialmonsender.ui.components.SettingsRow
import io.github.unofficialmonsender.ui.components.monFieldColors
import io.github.unofficialmonsender.ui.theme.MonColors

@Composable
fun SiteRulesScreen(
    settings: AppSettings,
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
            item { SectionHeader(stringResource(R.string.sites_always)) }
            item {
                HostList(
                    hosts = settings.allowedHosts,
                    emptyText = stringResource(R.string.sites_always_empty),
                    onAdd = { host -> onUpdate { it.copy(allowedHosts = it.allowedHosts + host, blockedHosts = it.blockedHosts - host) } },
                    onRemove = { host -> onUpdate { it.copy(allowedHosts = it.allowedHosts - host) } },
                )
            }
            item { SectionHeader(stringResource(R.string.sites_never)) }
            item {
                HostList(
                    hosts = settings.blockedHosts,
                    emptyText = stringResource(R.string.sites_never_empty),
                    onAdd = { host -> onUpdate { it.copy(blockedHosts = it.blockedHosts + host, allowedHosts = it.allowedHosts - host) } },
                    onRemove = { host -> onUpdate { it.copy(blockedHosts = it.blockedHosts - host) } },
                )
            }
        }
    }
}

@Composable
private fun HostList(
    hosts: Set<String>,
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
