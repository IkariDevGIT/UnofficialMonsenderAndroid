package io.github.unofficialmonsender.ui.filters

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.data.settings.AppFilterMode
import io.github.unofficialmonsender.data.settings.AppSettings
import io.github.unofficialmonsender.ui.components.GroupDivider
import io.github.unofficialmonsender.ui.components.SectionHeader
import io.github.unofficialmonsender.ui.components.SettingsGroup
import io.github.unofficialmonsender.ui.components.SettingsRow
import io.github.unofficialmonsender.util.InstalledApp
import io.github.unofficialmonsender.util.launchableApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppFilterScreen(
    settings: AppSettings,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<InstalledApp>?>(null) {
        value = withContext(Dispatchers.IO) { context.launchableApps() }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val selectionEnabled = settings.appFilterMode != AppFilterMode.ANY

    FilterScaffold(title = stringResource(R.string.apps_title), onBack = onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text(
                    text = stringResource(R.string.apps_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
                )
            }
            item {
                SettingsGroup(Modifier.selectableGroup()) {
                    AppFilterMode.entries.forEachIndexed { index, mode ->
                        if (index > 0) GroupDivider()
                        val selected = settings.appFilterMode == mode
                        SettingsRow(
                            title = stringResource(mode.label()),
                            modifier = Modifier.selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onUpdate { it.copy(appFilterMode = mode) } },
                            ),
                            leading = { RadioButton(selected = selected, onClick = null) },
                        )
                    }
                }
            }
            item { SectionHeader(stringResource(R.string.apps_list_header)) }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text(stringResource(R.string.apps_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                )
            }
            val list = apps
            if (list == null) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val filtered = list.filter {
                    query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
                }
                items(filtered, key = { it.packageName }) { app ->
                    val checked = app.packageName in settings.appFilterPackages
                    SettingsRow(
                        title = app.label,
                        subtitle = app.packageName,
                        modifier = Modifier.toggleable(
                            value = checked,
                            enabled = selectionEnabled,
                            role = Role.Checkbox,
                            onValueChange = { on ->
                                onUpdate {
                                    it.copy(appFilterPackages = if (on) it.appFilterPackages + app.packageName else it.appFilterPackages - app.packageName)
                                }
                            },
                        ),
                        leading = { AppIcon(app.packageName) },
                        trailing = { Checkbox(checked = checked, onCheckedChange = null, enabled = selectionEnabled) },
                    )
                }
            }
        }
    }
}

private fun AppFilterMode.label() = when (this) {
    AppFilterMode.ANY -> R.string.apps_mode_any
    AppFilterMode.ONLY_SELECTED -> R.string.apps_mode_only
    AppFilterMode.EXCEPT_SELECTED -> R.string.apps_mode_except
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(40.dp)) {
        icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(40.dp)) }
    }
}
