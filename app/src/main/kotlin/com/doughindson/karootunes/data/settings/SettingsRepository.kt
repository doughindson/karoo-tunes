package com.doughindson.karootunes.data.settings

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doughindson.karootunes.AppScope
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.settingsDataStore by preferencesDataStore(name = "settings")
private val SETTINGS_KEY = stringPreferencesKey("settings")

/** One shared, already-parsed view of the user's settings. */
class SettingsRepository(context: Context, appScope: AppScope) {
    private val dataStore = context.settingsDataStore

    /** Null until the stored settings have been read for the first time. */
    val state: StateFlow<Settings?> = dataStore.data
        .map { prefs -> decode(prefs[SETTINGS_KEY]) }
        .distinctUntilChanged()
        .stateIn(appScope, SharingStarted.Eagerly, null)

    val settings: Flow<Settings> = state.filterNotNull()

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { prefs ->
            prefs[SETTINGS_KEY] = AppJson.encodeToString(Settings.serializer(), transform(decode(prefs[SETTINGS_KEY])))
        }
    }

    private fun decode(json: String?): Settings = try {
        json?.let { AppJson.decodeFromString(Settings.serializer(), it) } ?: Settings()
    } catch (e: Exception) {
        Log.e(TAG, "Stored settings are unreadable, using defaults", e)
        Settings()
    }
}
