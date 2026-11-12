package com.doughindson.karootunes.data.auth

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doughindson.karootunes.data.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class StoredToken(
    val accessToken: String,
    val refreshToken: String?,
    /** Wall-clock time (epoch ms) after which the access token is no longer valid. */
    val expiresAtMs: Long,
    val scope: String? = null,
) {
    /** True when the token expires within [marginMs], so it is refreshed before Spotify rejects it. */
    fun isExpiring(nowMs: Long, marginMs: Long = 60_000): Boolean = nowMs >= expiresAtMs - marginMs
}

/** PKCE state for a login that is in progress. Persisted so login survives the app being killed meanwhile. */
@Serializable
data class PendingAuthorization(val state: String, val codeVerifier: String)

interface TokenStore {
    val token: Flow<StoredToken?>
    suspend fun saveToken(token: StoredToken?)
    suspend fun pending(): PendingAuthorization?
    suspend fun savePending(pending: PendingAuthorization?)
}

private val Context.authDataStore by preferencesDataStore(name = "auth")
private val TOKEN_KEY = stringPreferencesKey("token")
private val PENDING_KEY = stringPreferencesKey("pending")

/** Keeps credentials apart from settings; `allowBackup` is off, so they never leave the device. */
class DataStoreTokenStore(context: Context) : TokenStore {
    private val dataStore = context.authDataStore

    override val token: Flow<StoredToken?> = dataStore.data.map { it.read(TOKEN_KEY, StoredToken.serializer()) }

    override suspend fun saveToken(token: StoredToken?) = write(TOKEN_KEY, StoredToken.serializer(), token)

    override suspend fun pending(): PendingAuthorization? =
        dataStore.data.first().read(PENDING_KEY, PendingAuthorization.serializer())

    override suspend fun savePending(pending: PendingAuthorization?) =
        write(PENDING_KEY, PendingAuthorization.serializer(), pending)

    private fun <T> Preferences.read(key: Preferences.Key<String>, serializer: KSerializer<T>): T? =
        this[key]?.let { runCatching { AppJson.decodeFromString(serializer, it) }.getOrNull() }

    private suspend fun <T> write(key: Preferences.Key<String>, serializer: KSerializer<T>, value: T?) {
        dataStore.edit { prefs ->
            if (value == null) prefs.remove(key) else prefs[key] = AppJson.encodeToString(serializer, value)
        }
    }
}
