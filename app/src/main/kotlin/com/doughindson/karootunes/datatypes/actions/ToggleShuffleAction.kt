package com.doughindson.karootunes.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.doughindson.karootunes.KarooTunesExtension
import com.doughindson.karootunes.spotify.APIClientProvider
import com.doughindson.karootunes.spotify.PlayerAction
import com.doughindson.karootunes.spotify.PlayerStateProvider
import com.doughindson.karootunes.spotify.WebAPIClient
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ToggleShuffleAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooTunesExtension.TAG, "Toggle shuffle action called")

        val currentShuffleState = playerStateProvider.state.first().isShuffling == true
        val newShuffleState = !currentShuffleState

        val apiClient = apiClientProvider.getActiveAPIInstance().first()

        playerStateProvider.update { appState ->
            appState.copy(
                commandPending = appState.commandPending || (apiClient is WebAPIClient),
                isShuffling = newShuffleState,
                pendingActions = appState.pendingActions + PlayerAction.TOGGLE_SHUFFLE
            )
        }

        try {
            apiClient.toggleShuffle(newShuffleState)
        } finally {
            playerStateProvider.update { appState ->
                appState.copy(pendingActions = appState.pendingActions - PlayerAction.TOGGLE_SHUFFLE)
            }
        }
    }
}