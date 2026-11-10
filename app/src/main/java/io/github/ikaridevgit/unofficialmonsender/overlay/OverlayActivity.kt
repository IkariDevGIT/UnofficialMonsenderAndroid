package io.github.ikaridevgit.unofficialmonsender.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import io.github.ikaridevgit.unofficialmonsender.R
import io.github.ikaridevgit.unofficialmonsender.appContainer
import io.github.ikaridevgit.unofficialmonsender.data.settings.Pause
import io.github.ikaridevgit.unofficialmonsender.detection.ClipboardReader
import io.github.ikaridevgit.unofficialmonsender.detection.CopyDetectionService
import io.github.ikaridevgit.unofficialmonsender.detection.DetectionOutcome
import io.github.ikaridevgit.unofficialmonsender.detection.LinkOrigin
import io.github.ikaridevgit.unofficialmonsender.link.Links
import io.github.ikaridevgit.unofficialmonsender.ui.theme.MonsenderTheme
import io.github.ikaridevgit.unofficialmonsender.util.appLabel
import kotlinx.coroutines.launch

/**
 * Shows [SendOverlay] for deliberate requests: text shared to the app, the quick settings tile
 * (which reads the clipboard once this activity has focus). Both are logged like detected copies.
 */
class OverlayActivity : ComponentActivity() {
    private var request by mutableStateOf<OverlayRequest?>(null)
    private var options by mutableStateOf(OverlayOptions.DEFAULT)
    private var notice by mutableStateOf<Int?>(null)
    private var canPause by mutableStateOf(false)
    private var awaitingClipboard = false
    private var entryId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MonsenderTheme {
                val current = request
                val message = notice
                when {
                    current != null -> key(current) {
                        val id = entryId
                        SendOverlay(
                            request = current,
                            options = options,
                            send = { appContainer.linkSender.send(id, current.url) },
                            onClosed = {
                                closeEntry()
                                finish()
                            },
                            onPause = if (canPause) {
                                { appContainer.pauseDetection(Pause.quick()) }
                            } else {
                                null
                            },
                        )
                    }
                    message != null -> OverlayNotice(stringResource(message), onDone = ::finish)
                }
            }
        }
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && awaitingClipboard) readClipboard()
    }

    override fun onDestroy() {
        closeEntry()
        super.onDestroy()
    }

    private fun closeEntry() {
        entryId?.let { appContainer.detectionLog.promptClosed(it) }
        entryId = null
    }

    private fun handle(intent: Intent) {
        closeEntry()
        request = null
        notice = null
        awaitingClipboard = false
        if (intent.action == Intent.ACTION_SEND) {
            present(intent.getStringExtra(Intent.EXTRA_TEXT), sourcePackage = referrerPackage(), origin = LinkOrigin.SHARE)
        } else {
            awaitingClipboard = true
            if (hasWindowFocus()) readClipboard()
        }
    }

    private fun readClipboard() {
        awaitingClipboard = false
        present(ClipboardReader.read(this)?.text, sourcePackage = null, origin = LinkOrigin.TILE)
    }

    private fun present(text: String?, sourcePackage: String?, origin: LinkOrigin) {
        val url = Links.firstLink(text)
        if (url == null) {
            notice = R.string.overlay_no_link
            return
        }
        lifecycleScope.launch {
            val container = appContainer
            val settings = container.settings.current()
            options = OverlayOptions.from(settings)
            canPause = CopyDetectionService.running.value && settings.activePause() == null
            val check = container.linkInspector.inspect(url, sourcePackage, explicit = true)
            entryId = if (check.loggable) container.detectionLog.add(sourcePackage, url, DetectionOutcome.SHOWN, origin) else null
            request = OverlayRequest.from(check, sourceLabel = sourcePackage?.let { appLabel(it) }, origin = origin)
        }
    }

    private fun referrerPackage(): String? =
        referrer?.takeIf { it.scheme == "android-app" }?.host?.takeIf { it != packageName }

    companion object {
        fun clipboardIntent(context: Context) = Intent(context, OverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
