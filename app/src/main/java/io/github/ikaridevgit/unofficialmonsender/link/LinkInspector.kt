package io.github.ikaridevgit.unofficialmonsender.link

import io.github.ikaridevgit.unofficialmonsender.data.monloader.MonLoaderRepository
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SiteMatch
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SiteSupport
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppFilterMode
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppSettings
import io.github.ikaridevgit.unofficialmonsender.data.settings.SettingsRepository

enum class SkipReason { DETECTION_OFF, NOT_CONFIGURED, APP_FILTERED, SITE_BLOCKED, UNSUPPORTED }

data class LinkCheck(
    val url: String,
    val host: String,
    val site: SiteMatch,
    val skipReason: SkipReason?,
    /** Whether the link may be written to the log: logging is on and both its app and its site are allowed. */
    val loggable: Boolean,
) {
    val shouldShow: Boolean get() = skipReason == null
}

class LinkInspector(
    private val settings: SettingsRepository,
    private val monLoader: MonLoaderRepository,
) {
    /**
     * Decides whether a detected link earns an overlay. [explicit] requests (share, tile, test) come
     * from a deliberate user action, so the automatic-detection filters don't apply to them.
     */
    suspend fun inspect(url: String, sourcePackage: String?, explicit: Boolean): LinkCheck {
        val s = settings.current()
        val host = Links.hostOf(url).orEmpty()
        val site = monLoader.siteFor(url)
        val reason = if (explicit) null else skipReason(s, host, site, sourcePackage)
        val allowed = (sourcePackage == null || appAllowed(s, sourcePackage)) && !siteBlocked(s, host)
        return LinkCheck(url, host, site, reason, loggable = s.keepLog && allowed)
    }

    /** App and site rules come first, so nothing from a filtered app or blocked site is ever logged. */
    private fun skipReason(s: AppSettings, host: String, site: SiteMatch, sourcePackage: String?): SkipReason? {
        if (!s.detectionEnabled) return SkipReason.DETECTION_OFF
        if (sourcePackage != null && !appAllowed(s, sourcePackage)) return SkipReason.APP_FILTERED
        if (siteBlocked(s, host)) return SkipReason.SITE_BLOCKED
        if (!s.isConfigured) return SkipReason.NOT_CONFIGURED
        if (s.allowedHosts.any { Links.matchesHostRule(host, it) }) return null
        if (s.supportedSitesOnly && site.support == SiteSupport.UNKNOWN) return SkipReason.UNSUPPORTED
        return null
    }

    private fun siteBlocked(s: AppSettings, host: String): Boolean = s.blockedHosts.any { Links.matchesHostRule(host, it) }

    /** Whether copies in [pkg] may be looked at at all; checked before the clipboard is read. */
    fun watches(s: AppSettings, pkg: String?): Boolean = pkg == null || appAllowed(s, pkg)

    private fun appAllowed(s: AppSettings, pkg: String): Boolean = when (s.appFilterMode) {
        AppFilterMode.ANY -> true
        AppFilterMode.ONLY_SELECTED -> pkg in s.appFilterPackages
        AppFilterMode.EXCEPT_SELECTED -> pkg !in s.appFilterPackages
    }
}
