package io.github.unofficialmonsender.overlay

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
import io.github.unofficialmonsender.R
import io.github.unofficialmonsender.appContainer
import io.github.unofficialmonsender.detection.ClipboardReader
import io.github.unofficialmonsender.link.Links
import io.github.unofficialmonsender.ui.theme.MonsenderTheme
import io.github.unofficialmonsender.util.appLabel
import kotlinx.coroutines.launch

/**
 * Shows [SendOverlay] for deliberate requests: text shared to the app, the quick settings tile
 * (which reads the clipboard once this activity has focus), and the test button in settings.
 */
class OverlayActivity : ComponentActivity() {
    private var request by mutableStateOf<OverlayRequest?>(null)
    private var options by mutableStateOf(OverlayOptions.DEFAULT)
    private var notice by mutableStateOf<Int?>(null)
    private var awaitingClipboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MonsenderTheme {
                val current = request
                val message = notice
                when {
                    current != null -> key(current) {
                        SendOverlay(
                            request = current,
                            options = options,
                            send = { appContainer.monLoader.send(current.url) },
                            onClosed = ::finish,
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

    private fun handle(intent: Intent) {
        request = null
        notice = null
        awaitingClipboard = false
        when {
            intent.action == Intent.ACTION_SEND ->
                present(intent.getStringExtra(Intent.EXTRA_TEXT), sourcePackage = referrerPackage(), dryRun = false)

            intent.getStringExtra(EXTRA_MODE) == MODE_TEST ->
                present(intent.getStringExtra(EXTRA_URL), sourcePackage = null, dryRun = intent.getBooleanExtra(EXTRA_DRY_RUN, true))

            else -> {
                awaitingClipboard = true
                if (hasWindowFocus()) readClipboard()
            }
        }
    }

    private fun readClipboard() {
        awaitingClipboard = false
        present(ClipboardReader.read(this)?.text, sourcePackage = null, dryRun = false)
    }

    private fun present(text: String?, sourcePackage: String?, dryRun: Boolean) {
        val url = Links.firstLink(text)
        if (url == null) {
            notice = R.string.overlay_no_link
            return
        }
        lifecycleScope.launch {
            val container = appContainer
            options = OverlayOptions.from(container.settings.current())
            val check = container.linkInspector.inspect(url, sourcePackage, explicit = true)
            request = OverlayRequest.from(check, sourceLabel = sourcePackage?.let { appLabel(it) }, dryRun = dryRun)
        }
    }

    private fun referrerPackage(): String? =
        referrer?.takeIf { it.scheme == "android-app" }?.host?.takeIf { it != packageName }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_URL = "url"
        private const val EXTRA_DRY_RUN = "dry_run"
        private const val MODE_TEST = "test"

        fun clipboardIntent(context: Context) = Intent(context, OverlayActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun testIntent(context: Context, url: String, dryRun: Boolean) = Intent(context, OverlayActivity::class.java)
            .putExtra(EXTRA_MODE, MODE_TEST)
            .putExtra(EXTRA_URL, url)
            .putExtra(EXTRA_DRY_RUN, dryRun)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
