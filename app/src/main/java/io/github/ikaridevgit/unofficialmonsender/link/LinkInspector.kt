package io.github.ikaridevgit.unofficialmonsender.link

import io.github.ikaridevgit.unofficialmonsender.data.monloader.MonLoaderRepository
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SiteMatch
import io.github.ikaridevgit.unofficialmonsender.data.monloader.SiteSupport
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppFilterMode
import io.github.ikaridevgit.unofficialmonsender.data.settings.AppSettings
import io.github.ikaridevgit.unofficialmonsender.data.settings.SettingsRepository
import io.github.ikaridevgit.unofficialmonsender.data.settings.SiteMode

enum class SkipReason { NOT_CONFIGURED, APP_FILTERED, SITE_BLOCKED, UNSUPPORTED }

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
        val allowed = (sourcePackage == null || appAllowed(s, sourcePackage)) && !siteBlocked(s, host)
        val loggable = s.keepLog && allowed
        val early = if (explicit) null else ruleSkip(s, host, sourcePackage)
        if (early != null) return LinkCheck(url, host, SiteMatch(SiteSupport.UNKNOWN), early, loggable)
        val match = monLoader.siteFor(url)
        val image = Links.isImageUrl(url)
        val reason = if (explicit) null else siteSkip(s, host, match, image) ?: SkipReason.NOT_CONFIGURED.takeIf { !s.isConfigured }
        val site = if (image && match.support != SiteSupport.SUPPORTED) SiteMatch(SiteSupport.IMAGE) else match
        return LinkCheck(url, host, site, reason, loggable)
    }

    /**
     * App and site rules come first, so nothing from a filtered app or ignored site is ever logged,
     * and MonLoader isn't asked about its sites for a copy that is skipped anyway. Only a link the
     * site settings would watch can end up as "MonLoader isn't set up".
     */
    private fun ruleSkip(s: AppSettings, host: String, sourcePackage: String?): SkipReason? {
        if (sourcePackage != null && !appAllowed(s, sourcePackage)) return SkipReason.APP_FILTERED
        if (siteBlocked(s, host)) return SkipReason.SITE_BLOCKED
        return null
    }

    private fun siteSkip(s: AppSettings, host: String, site: SiteMatch, image: Boolean): SkipReason? {
        if (s.allowedHosts.any { Links.matchesHostRule(host, it) }) return null
        if (image && s.allowImages) return null
        return when (s.siteMode) {
            SiteMode.ALL -> null
            SiteMode.NONE -> SkipReason.UNSUPPORTED
            SiteMode.SUPPORTED -> if (site.support == SiteSupport.UNKNOWN) SkipReason.UNSUPPORTED else null
        }
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
