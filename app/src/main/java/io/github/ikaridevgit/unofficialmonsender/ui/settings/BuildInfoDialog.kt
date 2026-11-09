package io.github.ikaridevgit.unofficialmonsender.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ikaridevgit.unofficialmonsender.BuildConfig
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.ui.components.MonButton
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonColors

/** Everything worth pasting into a bug report, as (label, value) pairs. */
private fun buildDetails(context: Context): List<Pair<Int, String>> {
    val commit = BuildConfig.GIT_COMMIT + if (BuildConfig.GIT_DIRTY) " (uncommitted changes)" else ""
    return listOf(
        R.string.build_version to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        R.string.build_type to BuildConfig.BUILD_TYPE,
        R.string.build_commit to commit,
        R.string.build_commit_date to BuildConfig.GIT_COMMIT_DATE,
        R.string.build_package to context.packageName,
        R.string.build_targets to "Android ${context.applicationInfo.targetSdkVersion} (min ${context.applicationInfo.minSdkVersion})",
        R.string.build_device to "${Build.MANUFACTURER} ${Build.MODEL}",
        R.string.build_android to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    )
}

@Composable
fun BuildInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val details = remember(context) { buildDetails(context) }
    val labels = details.map { stringResource(it.first) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.border(1.dp, MonColors.border),
        containerColor = MonColors.surface,
        title = { Text(stringResource(R.string.build_info)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                details.forEachIndexed { index, (_, value) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = labels[index],
                            style = MaterialTheme.typography.bodySmall,
                            color = MonColors.fgDim,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(text = value, style = MaterialTheme.typography.bodySmall, color = MonColors.fg)
                    }
                }
            }
        },
        confirmButton = { MonButton(stringResource(R.string.action_close), onClick = onDismiss) },
        dismissButton = {
            MonButton(
                text = stringResource(R.string.action_copy),
                onClick = {
                    val text = details.indices.joinToString("\n") { "${labels[it]}: ${details[it].second}" }
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Build info", text))
                },
            )
        },
    )
}
