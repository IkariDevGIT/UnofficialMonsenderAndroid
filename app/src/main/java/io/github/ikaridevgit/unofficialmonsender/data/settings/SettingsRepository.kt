package io.github.ikaridevgit.unofficialmonsender.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        var result = AppSettings()
        store.edit { prefs ->
            result = transform(prefs.toSettings())
            prefs.write(result)
        }
        return result
    }

    private fun Preferences.toSettings() = AppSettings(
        serverUrl = this[Keys.serverUrl].orEmpty(),
        token = this[Keys.token].orEmpty(),
        pairedName = this[Keys.pairedName].orEmpty(),
        siteMode = this[Keys.siteMode]?.let { name -> SiteMode.entries.firstOrNull { it.name == name } }
            ?: if (this[Keys.supportedOnly] == false) SiteMode.ALL else SiteMode.SUPPORTED,
        allowImages = this[Keys.allowImages] ?: true,
        appFilterMode = this[Keys.appFilterMode]
            ?.let { name -> AppFilterMode.entries.firstOrNull { it.name == name } }
            ?: AppFilterMode.ANY,
        appFilterPackages = this[Keys.appFilterPackages].orEmpty(),
        allowedHosts = this[Keys.allowedHosts].orEmpty(),
        blockedHosts = this[Keys.blockedHosts].orEmpty(),
        autoDismissSeconds = this[Keys.autoDismiss] ?: 10,
        haptics = this[Keys.haptics] ?: true,
        welcomeSeen = this[Keys.welcomeSeen] ?: false,
        keepLog = this[Keys.keepLog] ?: true,
        pause = readPause(),
    )

    /** The old "Prompt for copied links" switch, when off, carries over as a pause until resumed. */
    private fun Preferences.readPause(): Pause? {
        if (this[Keys.pauseStarted] == null && this[Keys.detectionEnabled] == false) return Pause.indefinite(0L)
        val started = this[Keys.pauseStarted] ?: return null
        val until = this[Keys.pauseUntil] ?: return null
        val unit = this[Keys.pauseUnit]?.let { name -> PauseUnit.entries.firstOrNull { it.name == name } }
        return Pause(started, until, this[Keys.pauseAmount] ?: 0, unit)
    }

    private fun MutablePreferences.writePause(pause: Pause?) {
        if (pause == null) {
            remove(Keys.pauseStarted)
            remove(Keys.pauseUntil)
            remove(Keys.pauseAmount)
            remove(Keys.pauseUnit)
            return
        }
        this[Keys.pauseStarted] = pause.startedAt
        this[Keys.pauseUntil] = pause.until
        this[Keys.pauseAmount] = pause.amount
        if (pause.unit != null) this[Keys.pauseUnit] = pause.unit.name else remove(Keys.pauseUnit)
    }

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.serverUrl] = s.serverUrl
        this[Keys.token] = s.token
        this[Keys.pairedName] = s.pairedName
        remove(Keys.detectionEnabled)
        this[Keys.siteMode] = s.siteMode.name
        remove(Keys.supportedOnly)
        this[Keys.allowImages] = s.allowImages
        this[Keys.appFilterMode] = s.appFilterMode.name
        this[Keys.appFilterPackages] = s.appFilterPackages
        this[Keys.allowedHosts] = s.allowedHosts
        this[Keys.blockedHosts] = s.blockedHosts
        this[Keys.autoDismiss] = s.autoDismissSeconds
        this[Keys.haptics] = s.haptics
        this[Keys.welcomeSeen] = s.welcomeSeen
        this[Keys.keepLog] = s.keepLog
        writePause(s.pause)
    }

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val token = stringPreferencesKey("token")
        val pairedName = stringPreferencesKey("paired_name")
        val detectionEnabled = booleanPreferencesKey("detection_enabled")
        val supportedOnly = booleanPreferencesKey("supported_sites_only")
        val siteMode = stringPreferencesKey("site_mode")
        val allowImages = booleanPreferencesKey("allow_images")
        val appFilterMode = stringPreferencesKey("app_filter_mode")
        val appFilterPackages = stringSetPreferencesKey("app_filter_packages")
        val allowedHosts = stringSetPreferencesKey("allowed_hosts")
        val blockedHosts = stringSetPreferencesKey("blocked_hosts")
        val autoDismiss = intPreferencesKey("auto_dismiss_seconds")
        val haptics = booleanPreferencesKey("haptics")
        val welcomeSeen = booleanPreferencesKey("welcome_seen")
        val keepLog = booleanPreferencesKey("keep_log")
        val pauseStarted = longPreferencesKey("pause_started")
        val pauseUntil = longPreferencesKey("pause_until")
        val pauseAmount = intPreferencesKey("pause_amount")
        val pauseUnit = stringPreferencesKey("pause_unit")
    }
}
