package io.github.ikaridevgit.unofficialmonsender.detection

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import io.github.ikaridevgit.unofficialmonsender.appContainer
import io.github.ikaridevgit.unofficialmonsender.data.settings.Pause
import io.github.ikaridevgit.unofficialmonsender.link.Links
import io.github.ikaridevgit.unofficialmonsender.link.SkipReason
import io.github.ikaridevgit.unofficialmonsender.overlay.OverlayOptions
import io.github.ikaridevgit.unofficialmonsender.overlay.OverlayRequest
import io.github.ikaridevgit.unofficialmonsender.overlay.OverlayWindow
import io.github.ikaridevgit.unofficialmonsender.util.appLabel
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    private val launchable = HashMap<String, Boolean>()
    private var listenedEvents = 0
    @Volatile private var paused = false

    override fun onServiceConnected() {
        keyboards = getSystemService(InputMethodManager::class.java)
            ?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
        signals = CopySignalDetector(this)
        probe = ClipboardProbe(this)
        overlay = OverlayWindow(this)
        listenedEvents = serviceInfo?.eventTypes ?: 0
        _running.value = true
        scope.launch {
            appContainer.settings.settings.map { it.pause }.distinctUntilChanged().collectLatest(::followPause)
        }
    }

    /**
     * While a pause lasts, Android is told to send no events at all, so nothing can lead to a
     * clipboard read. The end is checked against the wall clock in short steps, since a delay
     * stands still while the phone sleeps.
     */
    private suspend fun followPause(pause: Pause?) {
        while (pause != null && pause.isActive(System.currentTimeMillis())) {
            listen(false)
            if (pause.indefinite) awaitCancellation()
            delay(pause.remaining(System.currentTimeMillis()).coerceIn(1L, PAUSE_RECHECK_MS))
        }
        listen(true)
        if (pause != null) appContainer.settings.update { if (it.pause == pause) it.copy(pause = null) else it }
    }

    private fun listen(on: Boolean) {
        paused = !on
        if (!on) pending?.cancel()
        val info = serviceInfo ?: return
        val types = if (on) listenedEvents else 0
        if (info.eventTypes != types) {
            info.eventTypes = types
            serviceInfo = info
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!_running.value || paused) return
        val pkg = event.packageName?.toString()
        if (pkg == packageName || (pkg != null && pkg in keyboards)) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && pkg != null && isUserApp(pkg)) {
            foregroundPackage = pkg
        }
        if (overlay.isShowing || inCooldown()) return
        if (!signals.isCopySignal(event)) return

        val source = sourceApp(pkg)
        pending?.cancel()
        pending = scope.launch {
            delay(SETTLE_DELAY_MS)
            handleCopy(source)
        }
    }

    private suspend fun handleCopy(source: String?) {
        val container = appContainer
        val settings = container.settings.current()
        if (!settings.detectionEnabled || settings.activePause() != null || paused || overlay.isShowing) return

        val log = container.detectionLog
        val clip = probe.read()
        lastProbeAt = SystemClock.elapsedRealtime()
        if (clip == null || clip == lastClip) return
        lastClip = clip

        if (container.linkSender.isOwnCopy(clip.text)) return
        val url = Links.firstLink(clip.text) ?: return
        val check = container.linkInspector.inspect(url, source, explicit = false)
        val skip = check.skipReason
        if (skip != null) {
            if (skip == SkipReason.NOT_CONFIGURED && check.loggable) log.notConfigured(source)
            return
        }
        val id = if (check.loggable) log.add(source, url) else null
        overlay.show(
            request = OverlayRequest.from(check, sourceLabel = source?.let { appLabel(it) }),
            options = OverlayOptions.from(settings),
            onDelivery = { delivery -> container.linkSender.report(id, delivery) },
            onClosed = { id?.let(log::promptClosed) },
        )
    }

    /**
     * The app the copy happened in. Only apps with a launcher entry count, so system components
     * that open windows of their own (biometric prompts, keyboards, SystemUI) are never named.
     */
    private fun sourceApp(eventPackage: String?): String? {
        eventPackage?.takeIf { isUserApp(it) }?.let { return it }
        val focused = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
        focused?.root?.packageName?.toString()?.takeIf { isUserApp(it) }?.let { return it }
        return foregroundPackage
    }

    private fun isUserApp(pkg: String): Boolean =
        pkg != packageName && launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null }

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

        private const val SETTLE_DELAY_MS = 300L
        private const val PAUSE_RECHECK_MS = 15_000L
        private const val PROBE_COOLDOWN_MS = 2500L
    }
}
