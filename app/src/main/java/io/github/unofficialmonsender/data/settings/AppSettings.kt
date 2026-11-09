package io.github.unofficialmonsender.data.settings

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
) {
    val isConfigured: Boolean get() = serverUrl.isNotEmpty()
    val isPaired: Boolean get() = token.isNotEmpty()

    companion object {
        val AUTO_DISMISS_CHOICES = listOf(0, 5, 10, 20)
    }
}
