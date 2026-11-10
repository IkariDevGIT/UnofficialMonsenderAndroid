package io.github.ikaridevgit.unofficialmonsender.ui.pause

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.data.settings.Pause
import io.github.ikaridevgit.unofficialmonsender.data.settings.PauseUnit
import io.github.ikaridevgit.unofficialmonsender.ui.components.ButtonRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonButton
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsGroup
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.StatusDot
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonColors
import io.github.ikaridevgit.unofficialmonsender.ui.theme.StatusColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The wall clock, ticking once a second while shown. */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L - System.currentTimeMillis() % 1_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
fun pauseLength(amount: Int, unit: PauseUnit): String = pluralStringResource(
    when (unit) {
        PauseUnit.SECONDS -> R.plurals.pause_seconds
        PauseUnit.MINUTES -> R.plurals.pause_minutes
        PauseUnit.HOURS -> R.plurals.pause_hours
        PauseUnit.DAYS -> R.plurals.pause_days
        PauseUnit.MONTHS -> R.plurals.pause_months
    },
    amount,
    amount,
)

@Composable
private fun unitWord(unit: PauseUnit, amount: Int): String = pluralStringResource(
    when (unit) {
        PauseUnit.SECONDS -> R.plurals.pause_unit_seconds
        PauseUnit.MINUTES -> R.plurals.pause_unit_minutes
        PauseUnit.HOURS -> R.plurals.pause_unit_hours
        PauseUnit.DAYS -> R.plurals.pause_unit_days
        PauseUnit.MONTHS -> R.plurals.pause_unit_months
    },
    amount,
)

/** Time left as a short clock: 4:09, 3h 05m or 2d 7h. */
fun remainingText(millis: Long): String {
    val seconds = (millis + 999) / 1000
    val days = seconds / 86_400
    val hours = seconds % 86_400 / 3_600
    val minutes = seconds % 3_600 / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h %02dm".format(minutes)
        else -> "$minutes:%02d".format(seconds % 60)
    }
}

/** "4:09 left of 30 minutes", or the open-ended line. */
@Composable
fun pauseStatus(pause: Pause, now: Long): String {
    val unit = pause.unit ?: return stringResource(R.string.paused_indefinite)
    return stringResource(R.string.paused_left, remainingText(pause.remaining(now)), pauseLength(pause.amount, unit))
}

/** "pause ›" style trailing hint, so a row reads as the way into the pause menu. */
@Composable
fun RowScope.SubmenuHint(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MonColors.link)
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MonColors.link)
}

/** Top-of-screen status on the Detections tab while detection runs; tapping it opens the pause menu. */
@Composable
fun DetectionOnCard(onOpenPause: () -> Unit) {
    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.detection_on),
            subtitle = stringResource(R.string.detection_on_detail),
            leading = { StatusDot(StatusColors.success) },
            trailing = { SubmenuHint(stringResource(R.string.pause_entry)) },
            onClick = onOpenPause,
        )
    }
}

/** Top-of-screen notice on the Detections tab while detection is paused. */
@Composable
fun PausedCard(pause: Pause, now: Long, onChange: () -> Unit, onResume: () -> Unit) {
    SettingsGroup {
        SettingsRow(
            title = stringResource(R.string.paused_title),
            subtitle = pauseStatus(pause, now),
            leading = { StatusDot(StatusColors.warning) },
        )
        if (!pause.indefinite) {
            Box(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(MonColors.input),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(1f - pause.progress(now))
                        .height(3.dp)
                        .background(StatusColors.warning),
                )
            }
        }
        ButtonRow {
            MonButton(stringResource(R.string.action_change), onClick = onChange)
            MonButton(stringResource(R.string.action_resume), onClick = onResume, accent = true)
        }
    }
}

/**
 * The pause menu: tap a length and detection pauses right away. While a pause runs, its status
 * and a resume button sit on top, and the chosen length is marked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PauseSheet(active: Pause?, onPause: (Pause) -> Unit, onResume: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val now = rememberNow()

    fun finish(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RectangleShape,
        containerColor = MonColors.surface,
        contentColor = MonColors.fg,
        scrimColor = MonColors.bg.copy(alpha = 0.6f),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(stringResource(R.string.pause_title), style = MaterialTheme.typography.titleLarge)
            Text(
                text = stringResource(R.string.pause_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MonColors.fgDim,
                modifier = Modifier.padding(top = 6.dp),
            )

            if (active != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .border(1.dp, StatusColors.warning)
                        .padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(StatusColors.warning)
                    Column(
                        Modifier
                            .padding(start = 12.dp)
                            .weight(1f),
                    ) {
                        Text(stringResource(R.string.paused_now), style = MaterialTheme.typography.labelLarge, color = StatusColors.warning)
                        Text(pauseStatus(active, now), style = MaterialTheme.typography.bodySmall, color = MonColors.fgDim)
                    }
                    MonButton(stringResource(R.string.action_resume_now), onClick = { finish(onResume) }, accent = true)
                }
            }

            SheetLabel(stringResource(if (active != null) R.string.pause_instead else R.string.pause_for))
            val choices = Pause.PRESETS.map { (n, u) -> PauseChoice(pauseLength(n, u), n, u) }
            choices.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { choice ->
                        PauseTile(
                            text = choice.label,
                            current = active?.amount == choice.amount && active.unit == choice.unit,
                            onClick = { finish { onPause(Pause.of(choice.amount, choice.unit)) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (pair.size == 1) {
                        PauseTile(
                            text = stringResource(R.string.pause_until_resumed_short),
                            current = active?.indefinite == true,
                            onClick = { finish { onPause(Pause.indefinite()) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            SheetLabel(stringResource(R.string.pause_custom))
            CustomPause(onPause = { pause -> finish { onPause(pause) } })
        }
    }
}

private class PauseChoice(val label: String, val amount: Int, val unit: PauseUnit)

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MonColors.fgDim,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun PauseTile(text: String, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(TILE_HEIGHT)
            .background(MonColors.input)
            .border(1.dp, if (current) StatusColors.warning else MonColors.border)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (current) StatusColors.warning else MonColors.fg,
            textAlign = TextAlign.Center,
        )
    }
}

/** One even row: amount, unit and a go button, all the same height as the tiles above. */
@Composable
private fun CustomPause(onPause: (Pause) -> Unit) {
    var amountText by rememberSaveable { mutableStateOf("2") }
    var unit by rememberSaveable { mutableStateOf(PauseUnit.HOURS) }
    var unitMenu by remember { mutableStateOf(false) }
    val amount = amountText.toIntOrNull()?.takeIf { it in 1..Pause.MAX_AMOUNT }
    val apply = { amount?.let { onPause(Pause.of(it, unit)) } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(72.dp)
                .height(TILE_HEIGHT)
                .background(MonColors.input)
                .border(1.dp, if (amount == null) MaterialTheme.colorScheme.error else MonColors.border),
            contentAlignment = Alignment.Center,
        ) {
            BasicTextField(
                value = amountText,
                onValueChange = { text -> amountText = text.filter(Char::isDigit).take(3) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MonColors.fg, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(MonColors.link),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TILE_HEIGHT)
                    .background(MonColors.input)
                    .border(1.dp, MonColors.border)
                    .clickable(role = Role.DropdownList) { unitMenu = true }
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = unitWord(unit, amount ?: 2),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = MonColors.fgDim)
            }
            DropdownMenu(
                expanded = unitMenu,
                onDismissRequest = { unitMenu = false },
                shape = MaterialTheme.shapes.small,
                containerColor = MonColors.surface,
                border = BorderStroke(1.dp, MonColors.border),
            ) {
                PauseUnit.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(unitWord(option, amount ?: 2), style = MaterialTheme.typography.bodyMedium) },
                        onClick = {
                            unit = option
                            unitMenu = false
                        },
                    )
                }
            }
        }
        MonButton(
            text = stringResource(R.string.action_pause),
            onClick = { apply() },
            enabled = amount != null,
            accent = true,
            modifier = Modifier.height(TILE_HEIGHT),
        )
    }
}

private val TILE_HEIGHT = 48.dp
