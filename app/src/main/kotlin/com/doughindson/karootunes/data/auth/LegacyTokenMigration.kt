package com.doughindson.karootunes.data.auth

import android.util.Log
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.AppJson
import com.doughindson.karootunes.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/** Moves a token saved by older versions (inside the settings blob) into [TokenStore]. Safe to run every start. */
suspend fun migrateLegacyToken(settings: SettingsRepository, store: TokenStore) {
    val legacy = settings.current().legacyToken ?: return
    if (store.token.first() == null) {
        runCatching { AppJson.decodeFromJsonElement(TokenResponse.serializer(), legacy) }
            .onSuccess { old ->
                // Expiry wasn't recorded, so treat it as expired and refresh on first use.
                store.saveToken(StoredToken(old.accessToken, old.refreshToken, expiresAtMs = 0, scope = old.scope))
                Log.i(TAG, "Migrated Spotify token from settings")
            }
            .onFailure { Log.w(TAG, "Could not migrate legacy token", it) }
    }
    settings.update { it.copy(legacyToken = null) }
}
