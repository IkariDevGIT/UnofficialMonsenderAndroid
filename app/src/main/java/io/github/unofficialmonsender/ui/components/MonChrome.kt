package io.github.unofficialmonsender.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.ui.theme.MonColors

/** Mon's header: a flat bar on the page color, ruled off underneath. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonTopBar(
    title: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        modifier = Modifier.rule(top = false),
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MonColors.bg,
            scrolledContainerColor = MonColors.bg,
            titleContentColor = MonColors.fg,
            navigationIconContentColor = MonColors.fg,
            actionIconContentColor = MonColors.fgDim,
        ),
    )
}

@Composable
fun MonPageTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

/** The wordmark with an "unofficial" tag, so it never passes for the real MonSender. */
@Composable
fun MonBrandTitle() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        MonWordmark()
        Text(
            text = stringResource(R.string.brand_unofficial),
            style = MaterialTheme.typography.labelSmall,
            color = MonColors.fgDim,
            modifier = Modifier
                .padding(start = 10.dp)
                .border(1.dp, MonColors.border)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

class MonTab(val label: String, val selected: Boolean, val onClick: () -> Unit)

/** Text tabs with an accent rule over the active one, like the nav links in Mon's header. */
@Composable
fun MonTabBar(tabs: List<MonTab>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MonColors.bg)
            .rule(top = true)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .selectableGroup(),
    ) {
        tabs.forEach { tab ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .selectable(selected = tab.selected, role = Role.Tab, onClick = tab.onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (tab.selected) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .width(56.dp)
                            .height(2.dp)
                            .background(MonColors.accent),
                    )
                }
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tab.selected) MonColors.fg else MonColors.fgDim,
                )
            }
        }
    }
}

/** Inputs sit on the darker input well with a plain border that turns accent when focused. */
@Composable
fun monFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MonColors.input,
    unfocusedContainerColor = MonColors.input,
    disabledContainerColor = MonColors.input,
    errorContainerColor = MonColors.input,
    focusedBorderColor = MonColors.accent,
    unfocusedBorderColor = MonColors.border,
    focusedLabelColor = MonColors.link,
    unfocusedLabelColor = MonColors.fgDim,
    focusedPlaceholderColor = MonColors.fgDim,
    unfocusedPlaceholderColor = MonColors.fgDim,
    focusedPrefixColor = MonColors.accent,
    unfocusedPrefixColor = MonColors.accent,
    cursorColor = MonColors.link,
)

/** The "> " prompt Mon puts in front of its inputs. */
@Composable
fun PromptPrefix() {
    Text("> ", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 2.dp))
}

/** Row of small toggles where the chosen one is a solid accent block, like monbooru's "Per page". */
@Composable
fun MonChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color.White else MonColors.fgDim,
                modifier = Modifier
                    .background(if (active) MonColors.accent else MonColors.input)
                    .border(1.dp, if (active) MonColors.accent else MonColors.border)
                    .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
fun ButtonRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
