package com.doughindson.karootunes

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.doughindson.karootunes.KarooTunesExtension.Companion.TAG
import com.doughindson.karootunes.auth.HttpException
import com.doughindson.karootunes.auth.OAuth2Client
import com.doughindson.karootunes.datatypes.PlayerDataType
import com.doughindson.karootunes.datatypes.PlayerViewProvider
import com.doughindson.karootunes.screens.KarooTunesSettings
import com.doughindson.karootunes.spotify.APIClientProvider
import com.doughindson.karootunes.spotify.LocalClient
import com.doughindson.karootunes.spotify.PlayerInPreviewModeProvider
import com.doughindson.karootunes.spotify.PlayerStateProvider
import com.doughindson.karootunes.spotify.ThumbnailCache
import com.doughindson.karootunes.spotify.WebAPIClient
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

class KarooSystemServiceProvider(private val context: Context) {
    val karooSystemService: KarooSystemService = KarooSystemService(context)

    private val _connectionState = MutableStateFlow(false)
    val connectionState = _connectionState.asStateFlow()

    init {
        karooSystemService.connect { connected ->
            if (connected) {
                Log.d(TAG, "Connected to Karoo system")
            }

            CoroutineScope(Dispatchers.IO).launch {
                _connectionState.emit(connected)
            }
        }
    }

    val settingsKey = stringPreferencesKey("settings")

    fun readSettings(settingsJson: String?): KarooTunesSettings {
        return if (settingsJson != null){
            jsonWithUnknownKeys.decodeFromString<KarooTunesSettings>(settingsJson)
        } else {
            val defaultSettings = jsonWithUnknownKeys.decodeFromString<KarooTunesSettings>(
                KarooTunesSettings.defaultSettings)

            defaultSettings.copy()
        }
    }

    suspend fun saveSettings(function: (settings: KarooTunesSettings) -> KarooTunesSettings) {
        context.dataStore.edit { t ->
            val settings = readSettings(t[settingsKey])
            val newSettings = function(settings)
            t[settingsKey] = jsonWithUnknownKeys.encodeToString(newSettings)
        }
    }

    fun streamSettings(): Flow<KarooTunesSettings> {
        return context.dataStore.data.map { settingsJson ->
            try {
                readSettings(settingsJson[settingsKey])
            } catch(e: Throwable){
                Log.e(TAG, "Failed to read preferences", e)
                jsonWithUnknownKeys.decodeFromString<KarooTunesSettings>(KarooTunesSettings.defaultSettings)
            }
        }.distinctUntilChanged()
    }

    fun streamRideState(): Flow<RideState> {
        return callbackFlow {
            val listenerId = karooSystemService.addConsumer { rideState: RideState ->
                trySendBlocking(rideState)
            }
            awaitClose {
                karooSystemService.removeConsumer(listenerId)
            }
        }
    }

    fun streamUserProfile(): Flow<UserProfile> {
        return callbackFlow {
            val listenerId = karooSystemService.addConsumer { userProfile: UserProfile ->
                trySendBlocking(userProfile)
            }
            awaitClose {
                karooSystemService.removeConsumer(listenerId)
            }
        }
    }

    fun showError(header: String, message: String, e: Throwable? = null) {
        var errorMessageString = message

        if (e is HttpException){
            try {
                // e.message here is just the generic "HTTP <status>" wrapper - the actual reason
                // Spotify gave (e.g. "Insufficient client scope") is in the raw response body.
                val deserializedError = jsonWithUnknownKeys.decodeFromString<WebAPIErrorResponse>(e.body.decodeToString())
                errorMessageString = "${deserializedError.error.status} - ${deserializedError.error.message}"
            } catch(parseError: Throwable) {
                Log.w(TAG, "Expected error to be in WebAPIErrorResponse format, but was not: ${parseError.message}")
            }
        }

        Log.e(TAG, "Error: $header - $errorMessageString", e)

        karooSystemService.dispatch(InRideAlert(id = "error-${System.currentTimeMillis()}", icon = R.drawable.spotify, title = header, detail = errorMessageString, autoDismissMs = 10_000L, backgroundColor = R.color.hRed, textColor = R.color.black))
    }
}

val appModule = module {
    singleOf(::KarooSystemServiceProvider)
    singleOf(::APIClientProvider)
    singleOf(::OAuth2Client)
    singleOf(::PlayerDataType)
    singleOf(::WebAPIClient)
    singleOf(::ThumbnailCache)
    singleOf(::LocalClient)
    singleOf(::PlayerStateProvider)
    singleOf(::PlayerInPreviewModeProvider)
    singleOf(::PlayerViewProvider)
    singleOf(::KarooTunesServices)
}

class KarooTunesApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@KarooTunesApplication)
            modules(appModule)
        }
    }
}