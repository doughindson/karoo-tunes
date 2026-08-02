package com.doughindson.karootunes.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.doughindson.karootunes.KarooTunesExtension
import com.doughindson.karootunes.spotify.APIClientProvider
import com.doughindson.karootunes.spotify.CachedTrackInfo
import com.doughindson.karootunes.spotify.PlayerAction
import com.doughindson.karootunes.spotify.PlayerStateProvider
import com.doughindson.karootunes.spotify.WebAPIClient
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.TimeSource

class NextAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val start = TimeSource.Monotonic.markNow()

        Log.d(KarooTunesExtension.TAG, "Next action called")

        val apiClient = apiClientProvider.getActiveAPIInstance().first()

        playerStateProvider.update { appState ->
            // Show the prefetched next track instantly if we have it; otherwise fall back to a
            // generic loading state (cleared grey artwork + "Loading next track") until the real
            // skip completes and the next poll confirms what's actually playing.
            val cachedNext = appState.upcomingTracksCache.firstOrNull()
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
                pendingActions = appState.pendingActions + PlayerAction.SKIP_NEXT,
                isPlayingTrackName = cachedNext?.trackName,
                isPlayingArtistName = cachedNext?.artistName,
                isPlayingTrackId = cachedNext?.trackId,
                isPlayingTrackUri = cachedNext?.trackUri,
                isPlayingTrackThumbnailUrls = cachedNext?.thumbnailUrls,
                currentTrackLengthInMs = cachedNext?.durationMs,
                playProgressInMs = 0,
                isTrackLiked = null,
                upcomingTracksCache = appState.upcomingTracksCache.drop(1),
                previousTrackCache = currentAsCached ?: appState.previousTrackCache
            )
        }

        try {
            apiClient.next()
        } finally {
            playerStateProvider.update { appState ->
                appState.copy(pendingActions = appState.pendingActions - PlayerAction.SKIP_NEXT)
            }
        }

        val runtime = TimeSource.Monotonic.markNow() - start
        Log.d(KarooTunesExtension.TAG, "Next action took $runtime")
    }
}