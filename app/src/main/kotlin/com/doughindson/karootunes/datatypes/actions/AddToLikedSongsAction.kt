package com.doughindson.karootunes.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.doughindson.karootunes.KarooSystemServiceProvider
import com.doughindson.karootunes.KarooTunesExtension
import com.doughindson.karootunes.spotify.APIClientProvider
import com.doughindson.karootunes.spotify.LocalClient
import com.doughindson.karootunes.spotify.PlayerAction
import com.doughindson.karootunes.spotify.PlayerStateProvider
import com.doughindson.karootunes.spotify.WebAPIClient
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AddToLikedSongsAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()
    private val karooSystemServiceProvider: KarooSystemServiceProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooTunesExtension.TAG, "Toggle liked songs action called")

        val playerState = playerStateProvider.state.first()
        val apiClient = apiClientProvider.getActiveAPIInstance().first()
        val wasLiked = playerState.isTrackLiked == true

        playerStateProvider.update { appState ->
            appState.copy(isTrackLiked = !wasLiked, pendingActions = appState.pendingActions + PlayerAction.ADD_TO_LIBRARY)
        }

        try {
            when (apiClient) {
                is WebAPIClient -> {
                    val trackId = playerState.isPlayingTrackId ?: return
                    if (wasLiked) {
                        apiClient.removeTrackFromLibrary(listOf(trackId))
                    } else {
                        apiClient.addTrackToLibrary(listOf(trackId))
                    }
                }

                is LocalClient -> {
                    val trackUri = playerState.isPlayingTrackUri ?: return
                    try {
                        if (wasLiked) {
                            apiClient.removeTrackFromLibrary(listOf(trackUri))
                        } else {
                            apiClient.addTrackToLibrary(context, listOf(trackUri))
                        }
                    } catch (e: Throwable) {
                        karooSystemServiceProvider.showError("Liked Songs", e.message ?: "Failed to update liked songs", e)
                    }
                }
            }
        } finally {
            playerStateProvider.update { appState ->
                appState.copy(pendingActions = appState.pendingActions - PlayerAction.ADD_TO_LIBRARY)
            }
        }
    }
}
