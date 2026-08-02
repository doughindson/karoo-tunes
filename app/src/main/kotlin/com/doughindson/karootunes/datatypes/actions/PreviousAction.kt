package com.doughindson.karootunes.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.doughindson.karootunes.KarooTunesExtension
import com.doughindson.karootunes.spotify.APIClientProvider
import com.doughindson.karootunes.spotify.CachedTrackInfo
import com.doughindson.karootunes.spotify.LocalClient
import com.doughindson.karootunes.spotify.PlayerAction
import com.doughindson.karootunes.spotify.PlayerStateProvider
import com.doughindson.karootunes.spotify.WebAPIClient
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.TimeSource

class PreviousAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val start = TimeSource.Monotonic.markNow()

        Log.d(KarooTunesExtension.TAG, "Previous action called")

        val state = playerStateProvider.state.first()
        val skipToPreviousDisabled = state.disabledActions.contains(PlayerAction.SKIP_PREVIOUS)
        val currentPlayProgress = state.playProgressInMs ?: 0
        val currentTrackLength = state.currentTrackLengthInMs

        val isAtEndOfTrack = currentTrackLength != null && currentPlayProgress >= currentTrackLength - 2000
        val isAtStartOfTrack = currentPlayProgress < 15_000

        val apiClient = apiClientProvider.getActiveAPIInstance().first()

        val restartTrack = apiClient is WebAPIClient && ((!isAtStartOfTrack && !isAtEndOfTrack) || skipToPreviousDisabled)

        if (restartTrack) {
            playerStateProvider.update { appState ->
                appState.copy(
                    commandPending = false,
                    playProgressInMs = 0,
                    isInOptionsMenu = false,
                    pendingActions = appState.pendingActions + PlayerAction.SEEK
                )
            }

            try {
                (apiClient as WebAPIClient).seek(0)
            } finally {
                playerStateProvider.update { appState ->
                    appState.copy(pendingActions = appState.pendingActions - PlayerAction.SEEK)
                }
            }

            val runtime = TimeSource.Monotonic.markNow() - start
            Log.d(KarooTunesExtension.TAG, "Previous action took $runtime")
            return
        }

        playerStateProvider.update { appState ->
            // Show the cached previous track instantly if we have it; otherwise fall back to a
            // generic loading state until the real skip completes and the next poll confirms it.
            val cachedPrevious = appState.previousTrackCache
            val currentAsCached = appState.isPlayingTrackId?.let {
                CachedTrackInfo(
                    trackId = appState.isPlayingTrackId,
                    trackUri = appState.isPlayingTrackUri,
                    trackName = appState.isPlayingTrackName,
                    artistName = appState.isPlayingArtistName,
                    thumbnailUrls = appState.isPlayingTrackThumbnailUrls,
                    durationMs = appState.currentTrackLengthInMs
                )
            }

            appState.copy(
                commandPending = appState.commandPending || (apiClient is WebAPIClient),
                isInOptionsMenu = false,
                pendingActions = appState.pendingActions + PlayerAction.SKIP_PREVIOUS,
                isPlayingTrackName = cachedPrevious?.trackName,
                isPlayingArtistName = cachedPrevious?.artistName,
                isPlayingTrackId = cachedPrevious?.trackId,
                isPlayingTrackUri = cachedPrevious?.trackUri,
                isPlayingTrackThumbnailUrls = cachedPrevious?.thumbnailUrls,
                currentTrackLengthInMs = cachedPrevious?.durationMs,
                playProgressInMs = 0,
                isTrackLiked = null,
                previousTrackCache = null,
                upcomingTracksCache = (listOfNotNull(currentAsCached) + appState.upcomingTracksCache).take(3)
            )
        }

        try {
            when (apiClient) {
                is LocalClient -> apiClient.previous()
                is WebAPIClient -> apiClient.previous()
                else -> error("Unknown API client type")
            }
        } finally {
            playerStateProvider.update { appState ->
                appState.copy(pendingActions = appState.pendingActions - PlayerAction.SKIP_PREVIOUS)
            }
        }

        val runtime = TimeSource.Monotonic.markNow() - start
        Log.d(KarooTunesExtension.TAG, "Previous action took $runtime")
    }
}