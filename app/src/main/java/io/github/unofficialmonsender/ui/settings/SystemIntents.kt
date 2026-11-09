package io.github.unofficialmonsender.ui.settings

import android.app.StatusBarManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.Settings
import androidx.annotation.RequiresApi
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.detection.CopyDetectionService
import io.github.unofficialmonsender.tile.SendClipboardTileService

fun Context.openAccessibilitySettings() {
    val component = ComponentName(this, CopyDetectionService::class.java).flattenToString()
    val details = Intent(ACTION_ACCESSIBILITY_DETAILS).putExtra(Intent.EXTRA_COMPONENT_NAME, component)
    try {
        startActivity(details)
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    } catch (_: SecurityException) {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

fun Context.openAppInfo() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}

/** Calls back with true when the tile was added or already present. */
@RequiresApi(33)
fun Context.requestAddTile(onResult: (Boolean) -> Unit) {
    val manager = getSystemService(StatusBarManager::class.java) ?: return onResult(false)
    manager.requestAddTileService(
        ComponentName(this, SendClipboardTileService::class.java),
        getString(R.string.tile_label),
        Icon.createWithResource(this, R.drawable.ic_tile_send),
        mainExecutor,
    ) { result ->
        onResult(
            result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED,
        )
    }
}

private const val ACTION_ACCESSIBILITY_DETAILS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
