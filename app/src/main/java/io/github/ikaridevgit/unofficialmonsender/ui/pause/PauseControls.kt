package io.github.ikaridevgit.unofficialmonsender.ui.pause

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.data.settings.Pause
import io.github.ikaridevgit.unofficialmonsender.data.settings.PauseUnit
import io.github.ikaridevgit.unofficialmonsender.ui.components.ButtonRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonButton
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsGroup
import io.github.ikaridevgit.unofficialmonsender.ui.components.SettingsRow
import io.github.ikaridevgit.unofficialmonsender.ui.components.StatusDot
import io.github.ikaridevgit.unofficialmonsender.ui.components.monFieldColors
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonColors
import io.github.ikaridevgit.unofficialmonsender.ui.theme.StatusColors
import kotlinx.coroutines.delay

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
 * Pick how long to pause: a preset, a custom length, or until turned back on. While a pause is
 * running, the dialog also offers to resume right away.
 */
@Composable
fun PauseDialog(active: Pause?, onPause: (Pause) -> Unit, onResume: () -> Unit, onDismiss: () -> Unit) {
    val now = rememberNow()
    var amountText by rememberSaveable { mutableStateOf("2") }
    var unit by rememberSaveable { mutableStateOf(PauseUnit.HOURS) }
    var unitMenu by remember { mutableStateOf(false) }
    val amount = amountText.toIntOrNull()?.takeIf { it in 1..Pause.MAX_AMOUNT }

    fun choose(pause: Pause) {
        onPause(pause)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, MonColors.border),
        containerColor = MonColors.surface,
        title = { Text(stringResource(R.string.pause_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.pause_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MonColors.fgDim,
                )
                if (active != null) {
                    Text(
                        text = stringResource(R.string.paused_title) + " · " + pauseStatus(active, now),
                        style = MaterialTheme.typography.labelLarge,
                        color = StatusColors.warning,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Pause.PRESETS.forEach { (n, u) ->
                    PauseOption(pauseLength(n, u)) { choose(Pause.of(n, u)) }
                }
                PauseOption(stringResource(R.string.pause_until_resumed)) { choose(Pause.indefinite()) }

                Text(
                    text = stringResource(R.string.pause_custom).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MonColors.fgDim,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { text -> amountText = text.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        isError = amount == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = monFieldColors(),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.width(88.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box {
                        MonButton(unitWord(unit, amount ?: 2) + " ▾", onClick = { unitMenu = true })
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
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    MonButton(
                        text = stringResource(R.string.action_pause),
                        onClick = { amount?.let { choose(Pause.of(it, unit)) } },
                        enabled = amount != null,
                    )
                }
            }
        },
        confirmButton = {
            if (active != null) {
                MonButton(
                    text = stringResource(R.string.action_resume),
                    onClick = {
                        onResume()
                        onDismiss()
                    },
                    accent = true,
                )
            }
        },
        dismissButton = { MonButton(stringResource(R.string.action_cancel), onClick = onDismiss) },
    )
}

@Composable
private fun PauseOption(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("> ", style = MaterialTheme.typography.bodyLarge, color = MonColors.accent)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MonColors.fg)
    }
}
