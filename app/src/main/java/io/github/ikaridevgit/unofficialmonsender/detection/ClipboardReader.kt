package io.github.ikaridevgit.unofficialmonsender.detection

import android.content.ClipboardManager
import android.content.Context
import java.security.MessageDigest

data class ClipSnapshot(val text: String, val timestamp: Long) {
    /** Tells clips apart without keeping their text; it can't be turned back into the clip. */
    fun fingerprint(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$timestamp\n$text".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

object ClipboardReader {
    private const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

    /**
     * Reads the primary clip. Android 10+ only answers while this app holds input focus (or is the
     * default keyboard); otherwise the clip reads as empty and this returns null. Clips flagged as
     * sensitive (passwords, codes) are never read.
     */
    fun read(context: Context): ClipSnapshot? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = runCatching { clipboard.primaryClip }.getOrNull() ?: return null
        val description = clip.description
        if (description?.extras?.getBoolean(EXTRA_IS_SENSITIVE) == true) return null
        if (clip.itemCount == 0) return null
        val item = clip.getItemAt(0)
        val text = item.text?.toString()
            ?: item.uri?.takeIf { it.scheme == "http" || it.scheme == "https" }?.toString()
            ?: return null
        return ClipSnapshot(text, description?.timestamp ?: 0L)
    }
}
