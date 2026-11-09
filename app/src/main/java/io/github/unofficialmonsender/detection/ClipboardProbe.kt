package io.github.unofficialmonsender.detection

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Briefly takes input focus with an invisible 1×1 accessibility overlay, because Android only lets
 * the focused app read the clipboard. Must be called on the main thread.
 */
class ClipboardProbe(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(WindowManager::class.java)

    suspend fun read(): ClipSnapshot? {
        val focused = CompletableDeferred<Unit>()
        val view = object : View(service) {
            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) focused.complete(Unit)
            }
        }
        var added = false
        return try {
            windowManager.addView(view, params())
            added = true
            withTimeoutOrNull(FOCUS_TIMEOUT_MS) { focused.await() } ?: return null
            ClipboardReader.read(service)
        } catch (_: RuntimeException) {
            null
        } finally {
            if (added) runCatching { windowManager.removeViewImmediate(view) }
        }
    }

    private fun params() = WindowManager.LayoutParams(
        1,
        1,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "ClipboardProbe"
    }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 800L
    }
}
