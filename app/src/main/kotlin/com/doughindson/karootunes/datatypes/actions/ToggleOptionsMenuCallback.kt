package com.doughindson.karootunes.datatypes.actions

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.doughindson.karootunes.spotify.PlayerStateProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ToggleOptionsMenuCallback : ActionCallback, KoinComponent {
    private val playerStateProvider: PlayerStateProvider by inject()

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        playerStateProvider.update { appState ->
            appState.copy(isInOptionsMenu = !appState.isInOptionsMenu)
        }
    }
}