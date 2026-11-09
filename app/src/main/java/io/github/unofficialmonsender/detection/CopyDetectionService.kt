package io.github.unofficialmonsender.detection

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import io.github.unofficialmonsender.appContainer
import io.github.unofficialmonsender.link.Links
import io.github.unofficialmonsender.overlay.OverlayOptions
import io.github.unofficialmonsender.overlay.OverlayRequest
import io.github.unofficialmonsender.overlay.OverlayWindow
import io.github.unofficialmonsender.util.appLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CopyDetectionService : AccessibilityService() {
    private val scope = MainScope()
    private lateinit var signals: CopySignalDetector
    private lateinit var probe: ClipboardProbe
    private lateinit var overlay: OverlayWindow

    private var pending: Job? = null
    private var foregroundPackage: String? = null
    private var lastClip: ClipSnapshot? = null
    private var lastProbeAt = 0L
    private var keyboards: Set<String> = emptySet()

    override fun onServiceConnected() {
        keyboards = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
        signals = CopySignalDetector(this)
        probe = ClipboardProbe(this)
        overlay = OverlayWindow(this)
        _running.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!_running.value) return
        val pkg = event.packageName?.toString()
        if (pkg == packageName || (pkg != null && pkg in keyboards)) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null && pkg !in SYSTEM_PACKAGES) {
            foregroundPackage = pkg
        }
        if (overlay.isShowing || inCooldown()) return
        val signal = signals.isCopySignal(event)
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "${AccessibilityEvent.eventTypeToString(event.eventType)} signal=$signal pkg=$pkg window=${event.windowId} text=${event.text} desc=${event.contentDescription}")
        }
        if (!signal) return

        val source = if (pkg == null || pkg in SYSTEM_PACKAGES) foregroundPackage else pkg
        pending?.cancel()
        pending = scope.launch {
            delay(SETTLE_DELAY_MS)
            handleCopy(source)
        }
    }

    private suspend fun handleCopy(source: String?) {
        val container = appContainer
        val settings = container.settings.current()
        if (!settings.detectionEnabled || overlay.isShowing) return

        val log = container.detectionLog
        val clip = probe.read()
        lastProbeAt = SystemClock.elapsedRealtime()
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "probe source=$source read=${clip != null}")
        if (clip == null) {
            log.add(source, null, DetectionOutcome.UNREADABLE)
            return
        }
        if (clip == lastClip) {
            log.add(source, null, DetectionOutcome.REPEATED)
            return
        }
        lastClip = clip

        val url = Links.firstLink(clip.text)
        if (url == null) {
            log.add(source, null, DetectionOutcome.NO_LINK)
            return
        }
        val check = container.linkInspector.inspect(url, source, explicit = false)
        val skip = check.skipReason
        if (skip != null) {
            log.add(source, url, DetectionOutcome.of(skip))
            return
        }
        val id = log.add(source, url, DetectionOutcome.SHOWN)
        overlay.show(
            request = OverlayRequest.from(check, sourceLabel = source?.let { appLabel(it) }),
            options = OverlayOptions.from(settings),
            onDelivery = { delivery ->
                log.setDelivery(id, delivery)
                if (delivery is Delivery.Queued && delivery.jobId != null) container.jobTracker.track(id, delivery.jobId)
            },
            onClosed = { log.promptClosed(id) },
        )
    }

    /** Reading the clipboard makes Android post its own notice, which must not count as a new copy. */
    private fun inCooldown() = SystemClock.elapsedRealtime() - lastProbeAt < PROBE_COOLDOWN_MS

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        _running.value = false
        pending?.cancel()
        if (::overlay.isInitialized) overlay.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val SYSTEM_PACKAGES = setOf(CopySignalDetector.SYSTEM_UI, "android")
        private const val SETTLE_DELAY_MS = 300L
        private const val PROBE_COOLDOWN_MS = 2500L
        private const val TAG = "CopyDetection"
    }
}
