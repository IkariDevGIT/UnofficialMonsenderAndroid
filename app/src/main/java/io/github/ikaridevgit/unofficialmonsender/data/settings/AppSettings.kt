package io.github.ikaridevgit.unofficialmonsender.data.settings

enum class AppFilterMode { ANY, ONLY_SELECTED, EXCEPT_SELECTED }

data class AppSettings(
    val serverUrl: String = "",
    val token: String = "",
    val pairedName: String = "",
    val detectionEnabled: Boolean = true,
    val supportedSitesOnly: Boolean = true,
    val appFilterMode: AppFilterMode = AppFilterMode.ANY,
    val appFilterPackages: Set<String> = emptySet(),
    val allowedHosts: Set<String> = emptySet(),
    val blockedHosts: Set<String> = emptySet(),
    val autoDismissSeconds: Int = 10,
    val haptics: Boolean = true,
    val welcomeSeen: Boolean = false,
    /** Off is the "Don't log, just send" mode: links are sent but never kept. */
    val keepLog: Boolean = true,
    val pause: Pause? = null,
) {
    val isConfigured: Boolean get() = serverUrl.isNotEmpty()
    val isPaired: Boolean get() = token.isNotEmpty()

    fun activePause(now: Long = System.currentTimeMillis()): Pause? = pause?.takeIf { it.isActive(now) }

    companion object {
        val AUTO_DISMISS_CHOICES = listOf(0, 5, 10, 20)
    }
}
