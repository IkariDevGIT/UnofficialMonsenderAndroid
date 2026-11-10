package io.github.ikaridevgit.unofficialmonsender.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonColors
import io.github.ikaridevgit.unofficialmonsender.ui.theme.Wordmark

/** "mon" plus the accented half, as every Mon project writes its name. */
@Composable
fun MonWordmark(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = MonColors.fg)) { append("mon") }
            withStyle(SpanStyle(color = MonColors.link)) { append("sender") }
        },
        fontFamily = Wordmark,
        fontSize = 22.sp,
        modifier = modifier,
    )
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
            .semantics { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .background(MonColors.accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MonColors.fgDim,
        )
    }
}

/** Bordered panel of related rows, separated by thin dividers. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MonColors.surface,
        border = BorderStroke(1.dp, MonColors.border),
    ) {
        Column(content = content)
    }
}

@Composable
fun GroupDivider() {
    HorizontalDivider(color = MonColors.border)
}

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MonColors.fg)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MonColors.fgDim,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                trailing()
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MonColors.accent,
                    checkedThumbColor = Color.White,
                    checkedBorderColor = MonColors.accent,
                    uncheckedTrackColor = MonColors.input,
                    uncheckedThumbColor = MonColors.fgDim,
                    uncheckedBorderColor = MonColors.border,
                ),
            )
        },
    )
}

/** A row that shows the chosen option and opens a menu of [options] when tapped. */
@Composable
fun DropdownRow(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    detail: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MonColors.fg)
                Text(
                    text = options.getOrElse(selected) { "" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MonColors.link,
                )
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MonColors.fgDim)
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MonColors.fgDim)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(16.dp, 0.dp),
            shape = MaterialTheme.shapes.small,
            containerColor = MonColors.surface,
            border = BorderStroke(1.dp, MonColors.border),
        ) {
            options.forEachIndexed { index, label ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index == selected) MonColors.link else MonColors.fg,
                        )
                    },
                    trailingIcon = if (index == selected) {
                        { Icon(Icons.Rounded.Check, contentDescription = null, tint = MonColors.link) }
                    } else {
                        null
                    },
                    onClick = {
                        open = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

@Composable
fun NavigationRow(title: String, subtitle: String?, onClick: () -> Unit) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MonColors.fgDim,
            )
        },
    )
}

/** Small square status marker, like the category swatches in monbooru's sidebar. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(8.dp)
            .background(color),
    )
}

/**
 * Mon's flat button: a bordered surface block, or a solid accent block for the main action
 * ([accent]) or a destructive one ([danger]).
 */
@Composable
fun MonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
    danger: Boolean = false,
) {
    val container = when {
        danger -> MonColors.danger
        accent -> MonColors.accent
        else -> MonColors.surface
    }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 36.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = if (accent || danger) null else BorderStroke(1.dp, MonColors.border),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = if (accent || danger) Color.White else MonColors.fg,
            disabledContainerColor = MonColors.surface,
            disabledContentColor = MonColors.fgDim.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Mon's inline "[action]" link. */
@Composable
fun MonLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MonColors.link,
    enabled: Boolean = true,
) {
    Text(
        text = "[$text]",
        style = MaterialTheme.typography.labelLarge,
        color = if (enabled) color else MonColors.fgDim.copy(alpha = 0.5f),
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

/** The thin rule Mon draws under its header and over its footer. */
fun Modifier.rule(top: Boolean): Modifier = drawBehind {
    val y = if (top) 0f else size.height - 1.dp.toPx()
    drawRect(MonColors.border, topLeft = Offset(0f, y), size = Size(size.width, 1.dp.toPx()))
}
