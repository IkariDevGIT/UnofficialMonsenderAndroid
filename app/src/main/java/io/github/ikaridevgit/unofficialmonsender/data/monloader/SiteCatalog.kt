package io.github.ikaridevgit.unofficialmonsender.data.monloader

import android.os.SystemClock
import io.github.ikaridevgit.unofficialmonsender.link.Links
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SiteSupport { SUPPORTED, IMAGE, UNKNOWN, CATALOG_UNAVAILABLE }

data class SiteMatch(val support: SiteSupport, val siteName: String? = null)

/** MonLoader's supported-site list, cached like MonSender does (5 min, empty results not cached). */
class SiteCatalog(private val api: MonLoaderApi) {
    private class Cached(val base: String, val at: Long, val sites: List<Site>)

    private val mutex = Mutex()
    private var cache: Cached? = null

    suspend fun match(base: String, token: String, url: String): SiteMatch {
        if (Links.isImageUrl(url)) return SiteMatch(SiteSupport.IMAGE)
        val host = Links.hostOf(url) ?: return SiteMatch(SiteSupport.UNKNOWN)
        val sites = sites(base, token)
        if (sites.isEmpty()) return SiteMatch(SiteSupport.CATALOG_UNAVAILABLE)
        val site = sites.firstOrNull { it.covers(host) } ?: return SiteMatch(SiteSupport.UNKNOWN)
        return SiteMatch(SiteSupport.SUPPORTED, site.displayName())
    }

    /** Every domain MonLoader's supported sites live on, for suggesting site rules. */
    suspend fun domains(base: String, token: String): List<String> =
        sites(base, token)
            .flatMap { site -> listOfNotNull(Links.hostOf(site.example)) + site.hosts.map { it.lowercase().removePrefix("www.") } }
            .filter { '.' in it }
            .distinct()
            .sorted()

    fun invalidate() {
        cache = null
    }

    private suspend fun sites(base: String, token: String): List<Site> = mutex.withLock {
        val now = SystemClock.elapsedRealtime()
        cache?.takeIf { it.base == base && now - it.at < TTL_MS }?.let { return it.sites }
        if (base.isEmpty() || token.isEmpty()) return emptyList()
        val response = api.listSites(base, token)
        val sites = if (response.isSuccessful) response.decode(SitesResponse.serializer())?.sites.orEmpty() else emptyList()
        if (sites.isNotEmpty()) cache = Cached(base, now, sites)
        sites
    }

    private fun Site.covers(host: String): Boolean {
        fun matches(candidate: String?) =
            !candidate.isNullOrEmpty() && (host == candidate || host.endsWith(".$candidate"))
        return matches(Links.hostOf(example)) || hosts.any { matches(it.lowercase().removePrefix("www.")) }
    }

    private fun Site.displayName(): String =
        name?.takeIf { it.isNotBlank() } ?: category.replaceFirstChar { it.uppercase() }

    private companion object {
        const val TTL_MS = 5 * 60 * 1000L
    }
}
