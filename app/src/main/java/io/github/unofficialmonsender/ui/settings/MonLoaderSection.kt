package io.github.unofficialmonsender.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.data.monloader.ConnectionState
import io.github.unofficialmonsender.data.monloader.PairingFailure
import io.github.unofficialmonsender.data.monloader.PairingManager
import io.github.unofficialmonsender.data.monloader.PairingState
import io.github.unofficialmonsender.data.settings.AppSettings
import io.github.unofficialmonsender.ui.components.GroupDivider
import io.github.unofficialmonsender.ui.components.SettingsGroup
import io.github.unofficialmonsender.ui.components.SettingsRow
import io.github.unofficialmonsender.ui.components.StatusDot
import io.github.unofficialmonsender.ui.theme.StatusColors

@Composable
fun MonLoaderSection(
    settings: AppSettings,
    connection: ConnectionState,
    pairing: PairingState,
    onSaveServerUrl: (String) -> Boolean,
    onRefresh: () -> Unit,
    onPair: () -> Unit,
    onCancelPairing: () -> Unit,
    onAcknowledgePairing: () -> Unit,
    onUnpair: () -> Unit,
) {
    SettingsGroup {
        ServerAddressField(settings.serverUrl, onSaveServerUrl)
        GroupDivider()
        ConnectionRow(connection, onRefresh)
        GroupDivider()
        PairingRow(settings, pairing, onPair, onCancelPairing, onAcknowledgePairing, onUnpair)
    }
}

@Composable
private fun ServerAddressField(saved: String, onSave: (String) -> Boolean) {
    var text by rememberSaveable(saved) { mutableStateOf(saved) }
    var invalid by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val dirty = text.trim() != saved

    fun save() {
        invalid = !onSave(text)
        if (!invalid) focus.clearFocus()
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            invalid = false
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
        label = { Text(stringResource(R.string.server_address)) },
        placeholder = { Text(stringResource(R.string.server_address_hint)) },
        singleLine = true,
        isError = invalid,
        supportingText = {
            Text(stringResource(if (invalid) R.string.server_address_invalid else R.string.server_address_help))
        },
        trailingIcon = if (dirty && text.isNotBlank()) {
            {
                IconButton(onClick = ::save) {
                    Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.action_save))
                }
            }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { if (dirty) save() else focus.clearFocus() }),
    )
}

@Composable
private fun ConnectionRow(state: ConnectionState, onRefresh: () -> Unit) {
    val (title, subtitle, color) = describe(state)
    SettingsRow(
        title = title,
        subtitle = subtitle,
        leading = {
            if (state == ConnectionState.Checking) {
                CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 2.dp)
            } else {
                StatusDot(color)
            }
        },
        trailing = {
            if (state != ConnectionState.NotConfigured) {
                IconButton(onClick = onRefresh, enabled = state != ConnectionState.Checking) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.action_check_again))
                }
            }
        },
    )
}

@Composable
private fun describe(state: ConnectionState): Triple<String, String?, Color> {
    val neutral = MaterialTheme.colorScheme.outline
    val error = MaterialTheme.colorScheme.error
    return when (state) {
        ConnectionState.NotConfigured -> Triple(stringResource(R.string.connection_not_configured), stringResource(R.string.connection_not_configured_detail), neutral)
        ConnectionState.Checking -> Triple(stringResource(R.string.connection_checking), null, neutral)
        is ConnectionState.Online -> Triple(
            stringResource(R.string.connection_online),
            listOfNotNull(
                state.version?.let { stringResource(R.string.connection_version, it) },
                state.galleryDlVersion?.let { stringResource(R.string.connection_gallerydl, it) },
            ).joinToString(" · ").ifEmpty { null },
            StatusColors.success,
        )
        ConnectionState.Unreachable -> Triple(stringResource(R.string.connection_unreachable), stringResource(R.string.connection_unreachable_detail), error)
        ConnectionState.NotMonLoader -> Triple(stringResource(R.string.connection_not_monloader), stringResource(R.string.connection_not_monloader_detail), error)
        ConnectionState.NotPaired -> Triple(stringResource(R.string.connection_reachable), stringResource(R.string.connection_not_paired_detail), StatusColors.warning)
        ConnectionState.TokenRejected -> Triple(stringResource(R.string.connection_token_rejected), stringResource(R.string.connection_token_rejected_detail), error)
        ConnectionState.SetupRequired -> Triple(stringResource(R.string.connection_setup), stringResource(R.string.connection_setup_detail), StatusColors.warning)
    }
}

@Composable
private fun PairingRow(
    settings: AppSettings,
    pairing: PairingState,
    onPair: () -> Unit,
    onCancel: () -> Unit,
    onAcknowledge: () -> Unit,
    onUnpair: () -> Unit,
) {
    var confirmUnpair by remember { mutableStateOf(false) }
    val busy = pairing == PairingState.Requesting || pairing is PairingState.WaitingForApproval

    LaunchedEffect(pairing) {
        if (pairing == PairingState.Paired) onAcknowledge()
    }

    AnimatedContent(targetState = Triple(busy, settings.isPaired, pairing as? PairingState.Failed), label = "pairing") { (isBusy, paired, failure) ->
        when {
            isBusy -> SettingsRow(
                title = stringResource(R.string.pairing_waiting),
                subtitle = stringResource(R.string.pairing_waiting_detail, (pairing as? PairingState.WaitingForApproval)?.name ?: PairingManager.APP_NAME),
                leading = { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                trailing = { TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) } },
            )

            paired -> SettingsRow(
                title = stringResource(R.string.pairing_paired),
                subtitle = stringResource(R.string.pairing_paired_detail, settings.pairedName.ifEmpty { PairingManager.APP_NAME }),
                trailing = { OutlinedButton(onClick = { confirmUnpair = true }) { Text(stringResource(R.string.action_unpair)) } },
            )

            else -> Column {
                SettingsRow(
                    title = stringResource(if (failure != null) R.string.pairing_failed else R.string.pairing_not_paired),
                    subtitle = if (failure != null) pairingFailureText(failure.reason) else stringResource(R.string.pairing_not_paired_detail),
                    trailing = {
                        if (failure != null) {
                            FilledTonalButton(onClick = onPair, enabled = settings.isConfigured) { Text(stringResource(R.string.action_try_again)) }
                        } else {
                            Button(onClick = onPair, enabled = settings.isConfigured) { Text(stringResource(R.string.action_pair)) }
                        }
                    },
                )
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text(stringResource(R.string.unpair_title)) },
            text = { Text(stringResource(R.string.unpair_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    onUnpair()
                }) { Text(stringResource(R.string.action_unpair)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun pairingFailureText(reason: PairingFailure): String = when (reason) {
    PairingFailure.NOT_CONFIGURED -> stringResource(R.string.pairing_failure_not_configured)
    PairingFailure.UNREACHABLE -> stringResource(R.string.pairing_failure_unreachable)
    PairingFailure.ALREADY_PAIRED -> stringResource(R.string.pairing_failure_already_paired, PairingManager.APP_NAME)
    PairingFailure.DENIED -> stringResource(R.string.pairing_failure_denied)
    PairingFailure.EXPIRED -> stringResource(R.string.pairing_failure_expired)
    PairingFailure.TOO_MANY_REQUESTS -> stringResource(R.string.pairing_failure_too_many)
    PairingFailure.REFUSED -> stringResource(R.string.pairing_failure_refused)
}
