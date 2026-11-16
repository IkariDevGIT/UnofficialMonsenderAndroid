package io.github.ikaridevgit.unofficialmonsender.data.monloader

import android.os.SystemClock
import io.github.ikaridevgit.unofficialmonsender.link.Links
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

enum class SiteSupport { SUPPORTED, IMAGE, UNKNOWN, CATALOG_UNAVAILABLE }

data class SiteMatch(val support: SiteSupport, val siteName: String? = null)

/**
 * MonLoader's supported-site list, kept in [file] so it still works without a connection. After
 * 30 minutes, or once invalidated, it is fetched again, but the stored list is only replaced when
 * that succeeds. Until then the old one keeps being used.
 */
class SiteCatalog(private val api: MonLoaderApi, private val file: File, private val scope: CoroutineScope) {
    @Serializable
    private class Stored(val base: String, val fetchedAt: Long, val sites: List<Site>)

    private val mutex = Mutex()
    private var cache: Stored? = null
    private var loaded = false
    private var invalidated = false
    private var lastFailure = 0L
    private var refreshing: Job? = null

    /** How MonLoader handles the link's site. Whether it is a direct image is left to the caller. */
    suspend fun match(base: String, token: String, url: String): SiteMatch {
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

    /** Marks the list as out of date. It stays stored and in use until a new one arrives. */
    fun invalidate() {
        invalidated = true
        lastFailure = 0L
    }

    /**
     * A stored list for this server is used right away, so a copied link never waits on the
     * network; when it's out of date a new one is fetched in the background. Only without any
     * list for this server does the caller wait for the fetch.
     */
    private suspend fun sites(base: String, token: String): List<Site> {
        val stored = mutex.withLock { stored() }
        if (stored != null && stored.base == base) {
            if (stored.isStale()) refreshLater(base, token)
            return stored.sites
        }
        return fetch(base, token) ?: stored?.sites.orEmpty()
    }

    private suspend fun stored(): Stored? {
        if (!loaded) {
            cache = withContext(Dispatchers.IO) { load() }
            loaded = true
        }
        return cache
    }

    private fun refreshLater(base: String, token: String) {
        if (refreshing?.isActive == true) return
        refreshing = scope.launch { fetch(base, token) }
    }

    /** Null when no new list could be fetched. */
    private suspend fun fetch(base: String, token: String): List<Site>? = mutex.withLock {
        stored()?.takeIf { it.base == base && !it.isStale() }?.let { return it.sites }
        val retryLater = SystemClock.elapsedRealtime() - lastFailure < RETRY_MS && lastFailure != 0L
        if (base.isEmpty() || token.isEmpty() || retryLater) return null

        val response = api.listSites(base, token)
        val sites = if (response.isSuccessful) response.decode(SitesResponse.serializer())?.sites.orEmpty() else emptyList()
        if (sites.isEmpty()) {
            lastFailure = SystemClock.elapsedRealtime()
            return null
        }
        val replacement = Stored(base, System.currentTimeMillis(), sites)
        cache = replacement
        invalidated = false
        lastFailure = 0L
        withContext(Dispatchers.IO) { save(replacement) }
        sites
    }

    private fun Stored.isStale(): Boolean =
        invalidated || System.currentTimeMillis() - fetchedAt !in 0 until TTL_MS

    private fun load(): Stored? = runCatching {
        if (file.exists()) json.decodeFromString(Stored.serializer(), file.readText()) else null
    }.getOrNull()

    private fun save(stored: Stored) {
        val temp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            temp.writeText(json.encodeToString(Stored.serializer(), stored))
            if (!temp.renameTo(file)) temp.delete()
        }.onFailure { temp.delete() }
    }

    private fun Site.covers(host: String): Boolean {
        fun matches(candidate: String?) =
            !candidate.isNullOrEmpty() && (host == candidate || host.endsWith(".$candidate"))
        return matches(Links.hostOf(example)) || hosts.any { matches(it.lowercase().removePrefix("www.")) }
    }

    private fun Site.displayName(): String =
        name?.takeIf { it.isNotBlank() } ?: category.replaceFirstChar { it.uppercase() }

    private companion object {
        const val TTL_MS = 30 * 60 * 1000L
        const val RETRY_MS = 60 * 1000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
