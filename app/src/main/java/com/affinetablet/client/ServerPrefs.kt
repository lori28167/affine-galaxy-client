package com.affinetablet.client

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "affine_prefs")

/** Persists the single self-hosted AFFiNE server URL and a couple of display preferences. */
class ServerPrefs(private val context: Context) {

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val DESKTOP_SITE = booleanPreferencesKey("desktop_site")
    }

    val serverUrl: Flow<String?> = context.dataStore.data.map { it[Keys.SERVER_URL] }
    val desktopSite: Flow<Boolean> = context.dataStore.data.map { it[Keys.DESKTOP_SITE] ?: true }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[Keys.SERVER_URL] = url }
    }

    suspend fun setDesktopSite(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DESKTOP_SITE] = enabled }
    }

    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}

/** Normalizes user input into a full https:// (or http://) URL, or null if unusable. */
fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "https://$trimmed"
    }
    return runCatching {
        val uri = java.net.URI(withScheme)
        if (uri.host.isNullOrBlank()) null else withScheme.trimEnd('/')
    }.getOrNull()
}
