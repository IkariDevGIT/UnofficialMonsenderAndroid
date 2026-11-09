package io.github.unofficialmonsender.detection

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.view.accessibility.AccessibilityEvent

/**
 * Android has no clipboard-changed event for background apps, so copies are inferred from what the
 * user sees: tapping a "Copy…" control, a "Copied" toast, or the system clipboard preview. A false
 * positive only costs a clipboard read; repeated clips are filtered afterwards.
 */
class CopySignalDetector(context: Context) {
    private val copyWords: List<String> = listOfNotNull(
        context.getString(android.R.string.copy),
        context.getString(android.R.string.copyUrl),
        "copy",
    ).map { it.lowercase() }.distinct()

    private val copiedWords: List<String> = listOfNotNull(
        systemString("text_copied"),
        systemString("copied"),
        "copied",
    ).map { it.lowercase() }.distinct()

    @Suppress("DEPRECATION")
    fun isCopySignal(event: AccessibilityEvent): Boolean {
        val text = buildString {
            event.text.forEach { append(it).append(' ') }
            event.contentDescription?.let { append(it) }
        }.trim().lowercase()
        if (text.isEmpty()) return false

        return when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ->
                text.length <= MAX_CONTROL_LABEL && copyWords.any { it in text }

            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_ANNOUNCEMENT ->
                copiedWords.any { it in text }

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ->
                event.packageName == SYSTEM_UI && copiedWords.any { it in text }

            else -> false
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun systemString(name: String): String? {
        val res = Resources.getSystem()
        val id = res.getIdentifier(name, "string", "android")
        return if (id != 0) runCatching { res.getString(id) }.getOrNull() else null
    }

    companion object {
        const val SYSTEM_UI = "com.android.systemui"
        private const val MAX_CONTROL_LABEL = 48
    }
}
