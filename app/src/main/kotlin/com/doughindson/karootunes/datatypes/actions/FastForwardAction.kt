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
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class FastForwardAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooTunesExtension.TAG, "Fast forward action called")

        val state = playerStateProvider.state.first()
        val newPosition = state.playProgressInMs?.plus(SEEK_MS)?.coerceAtMost(state.currentTrackLengthInMs ?: Int.MAX_VALUE)

        if (newPosition != null){
            val apiClient = apiClientProvider.getActiveAPIInstance().first()

            playerStateProvider.update { appState ->
                appState.copy(playProgressInMs = newPosition, pendingActions = appState.pendingActions + PlayerAction.SEEK)
            }

            try {
                apiClient.seek(newPosition)
            } finally {
                playerStateProvider.update { appState ->
                    appState.copy(pendingActions = appState.pendingActions - PlayerAction.SEEK)
                }
            }
        }
    }
}