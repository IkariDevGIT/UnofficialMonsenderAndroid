package io.github.unofficialmonsender.detection

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Android has no clipboard-changed event for background apps, so copies are inferred from
 * language-independent UI structure: a toast, a new system window (Android 13+ shows a clipboard
 * preview on every copy), or a tap that closes the popup it was in (context menus, the text
 * selection toolbar, share sheets). Labels containing the system's localized "Copy" are a cheap
 * extra signal. A false positive only costs a clipboard read; unchanged clips are dropped later.
 */
class CopySignalDetector(private val service: AccessibilityService) {
    private val copyWords: List<String> = listOf(
        service.getString(android.R.string.copy),
        service.getString(android.R.string.copyUrl),
        "copy",
    ).map { it.lowercase() }.distinct()

    private var lastClickWindowId = -1
    private var lastClickAt = 0L

    @Suppress("DEPRECATION")
    fun isCopySignal(event: AccessibilityEvent): Boolean = when (event.eventType) {
        AccessibilityEvent.TYPE_VIEW_CLICKED,
        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
            lastClickWindowId = event.windowId
            lastClickAt = SystemClock.uptimeMillis()
            labelsOf(event).any { label -> copyWords.any { it in label } }
        }

        AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED ->
            event.className?.contains("Toast") == true

        AccessibilityEvent.TYPE_ANNOUNCEMENT ->
            event.packageName == SYSTEM_UI

        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> windowSignal(event)

        else -> false
    }

    private fun windowSignal(event: AccessibilityEvent): Boolean {
        if (Build.VERSION.SDK_INT < 28) return false
        val changes = event.windowChanges
        if (changes and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0) {
            val closedByTap = event.windowId == lastClickWindowId &&
                SystemClock.uptimeMillis() - lastClickAt < POPUP_CLOSE_WINDOW_MS
            if (closedByTap) {
                lastClickWindowId = -1
                return true
            }
        }
        if (changes and AccessibilityEvent.WINDOWS_CHANGE_ADDED != 0) {
            val window = service.windows.firstOrNull { it.id == event.windowId } ?: return false
            return window.type == AccessibilityWindowInfo.TYPE_SYSTEM && window.ownerPackage() == SYSTEM_UI
        }
        return false
    }

    private fun AccessibilityWindowInfo.ownerPackage(): String? =
        runCatching { root?.packageName?.toString() }.getOrNull()

    private fun labelsOf(event: AccessibilityEvent): List<String> = buildList {
        event.text.joinToString(" ").takeIf { it.isNotBlank() }?.let(::add)
        event.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(::add)
    }.map { it.lowercase() }

    companion object {
        const val SYSTEM_UI = "com.android.systemui"
        private const val POPUP_CLOSE_WINDOW_MS = 1000L
    }
}
