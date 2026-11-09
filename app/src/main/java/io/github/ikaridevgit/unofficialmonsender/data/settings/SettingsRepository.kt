package io.github.ikaridevgit.unofficialmonsender.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
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
        detectionEnabled = this[Keys.detectionEnabled] ?: true,
        supportedSitesOnly = this[Keys.supportedOnly] ?: true,
        appFilterMode = this[Keys.appFilterMode]
            ?.let { name -> AppFilterMode.entries.firstOrNull { it.name == name } }
            ?: AppFilterMode.ANY,
        appFilterPackages = this[Keys.appFilterPackages].orEmpty(),
        allowedHosts = this[Keys.allowedHosts].orEmpty(),
        blockedHosts = this[Keys.blockedHosts].orEmpty(),
        autoDismissSeconds = this[Keys.autoDismiss] ?: 10,
        haptics = this[Keys.haptics] ?: true,
        welcomeSeen = this[Keys.welcomeSeen] ?: false,
    )

    private fun MutablePreferences.write(s: AppSettings) {
        this[Keys.serverUrl] = s.serverUrl
        this[Keys.token] = s.token
        this[Keys.pairedName] = s.pairedName
        this[Keys.detectionEnabled] = s.detectionEnabled
        this[Keys.supportedOnly] = s.supportedSitesOnly
        this[Keys.appFilterMode] = s.appFilterMode.name
        this[Keys.appFilterPackages] = s.appFilterPackages
        this[Keys.allowedHosts] = s.allowedHosts
        this[Keys.blockedHosts] = s.blockedHosts
        this[Keys.autoDismiss] = s.autoDismissSeconds
        this[Keys.haptics] = s.haptics
        this[Keys.welcomeSeen] = s.welcomeSeen
    }

    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val token = stringPreferencesKey("token")
        val pairedName = stringPreferencesKey("paired_name")
        val detectionEnabled = booleanPreferencesKey("detection_enabled")
        val supportedOnly = booleanPreferencesKey("supported_sites_only")
        val appFilterMode = stringPreferencesKey("app_filter_mode")
        val appFilterPackages = stringSetPreferencesKey("app_filter_packages")
        val allowedHosts = stringSetPreferencesKey("allowed_hosts")
        val blockedHosts = stringSetPreferencesKey("blocked_hosts")
        val autoDismiss = intPreferencesKey("auto_dismiss_seconds")
        val haptics = booleanPreferencesKey("haptics")
        val welcomeSeen = booleanPreferencesKey("welcome_seen")
    }
}
