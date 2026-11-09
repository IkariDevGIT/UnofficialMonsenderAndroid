package io.github.unofficialmonsender.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.unofficialmonsender.overlay.OverlayActivity

/** Opens the send overlay for whatever link is on the clipboard. Works without the accessibility service. */
class SendClipboardTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        if (isLocked) unlockAndRun(::launchOverlay) else launchOverlay()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchOverlay() {
        val intent = OverlayActivity.clipboardIntent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
