package io.github.unofficialmonsender.data.monloader

import io.github.unofficialmonsender.data.settings.SettingsRepository
import io.github.unofficialmonsender.link.Links
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MonLoaderRepository(
    private val settings: SettingsRepository,
    private val api: MonLoaderApi,
    private val catalog: SiteCatalog,
    private val scope: CoroutineScope,
) {
    suspend fun checkConnection(): ConnectionState {
        val s = settings.current()
        if (!s.isConfigured) return ConnectionState.NotConfigured
        val health = api.health(s.serverUrl)
        if (health.isNetworkError) return ConnectionState.Unreachable
        val authed = api.listQueue(s.serverUrl, s.token)
        return Outcomes.connection(health, authed, s.isPaired)
    }

    suspend fun send(url: String): SendResult {
        val s = settings.current()
        if (!s.isConfigured) return SendResult.Failed(SendFailure.NOT_CONFIGURED)
        if (!s.isPaired) return SendResult.Failed(SendFailure.NOT_PAIRED)
        return Outcomes.send(api.enqueue(s.serverUrl, s.token, url), tokenHeld = true)
    }

    suspend fun siteFor(url: String): SiteMatch {
        val s = settings.current()
        return catalog.match(s.serverUrl, s.token, url)
    }

    /**
     * Saves a new server address. A token belongs to the instance that issued it, so changing the
     * address drops it and tells the old instance, as MonSender does.
     */
    suspend fun setServerUrl(raw: String): String? {
        val url = Links.normalizeServerUrl(raw) ?: return null
        val previous = settings.current()
        settings.update { s -> if (s.serverUrl == url) s else s.copy(serverUrl = url, token = "", pairedName = "") }
        if (previous.serverUrl != url) {
            catalog.invalidate()
            if (previous.isPaired) scope.launch { api.pairRemove(previous.serverUrl, previous.token) }
        }
        return url
    }

    /** Returns false when MonLoader could not be told, so its token must be revoked there by hand. */
    suspend fun unpair(): Boolean {
        val s = settings.current()
        if (!s.isPaired) return true
        val response = api.pairRemove(s.serverUrl, s.token)
        settings.update { it.copy(token = "", pairedName = "") }
        catalog.invalidate()
        return response.isSuccessful || response.status == 401
    }
}
