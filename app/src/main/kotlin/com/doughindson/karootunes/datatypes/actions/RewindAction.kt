package com.doughindson.karootunes.datatypes.actions

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.doughindson.karootunes.KarooTunesExtension
import com.doughindson.karootunes.spotify.APIClientProvider
import com.doughindson.karootunes.spotify.PlayerStateProvider
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

const val SEEK_MS = 30_000

class RewindAction : ActionCallback, KoinComponent {
    private val apiClientProvider: APIClientProvider by inject()
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        Log.d(KarooTunesExtension.TAG, "Rewind action called")

        val state = playerStateProvider.state.first()
        val newPosition = state.playProgressInMs?.minus(SEEK_MS)?.coerceAtLeast(0)

        val apiClient = apiClientProvider.getActiveAPIInstance().first()

        if (newPosition != null){
            apiClient.seek(newPosition)

            playerStateProvider.update { appState ->
                appState.copy(playProgressInMs = newPosition)
            }
        }
    }
}