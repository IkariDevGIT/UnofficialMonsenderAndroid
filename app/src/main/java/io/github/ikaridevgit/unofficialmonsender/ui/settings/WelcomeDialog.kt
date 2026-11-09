package io.github.ikaridevgit.unofficialmonsender.ui.settings

import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonButton
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonColors

@Composable
fun WelcomeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, MonColors.border),
        containerColor = MonColors.surface,
        title = { Text(stringResource(R.string.welcome_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.welcome_accessibility))
                if (Build.VERSION.SDK_INT >= 33) Text(stringResource(R.string.welcome_restricted))
            }
        },
        confirmButton = {
            MonButton(stringResource(R.string.action_got_it), onClick = onDismiss, accent = true)
        },
    )
}
