package com.doughindson.karootunes.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.doughindson.karootunes.AppScope
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.playback.PlayerCommand
import com.doughindson.karootunes.playback.PlayerCommands
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The one click handler for every widget button; the button's [PlayerCommand] arrives as a parameter. */
class PlayerActionCallback : ActionCallback, KoinComponent {
    private val commands: PlayerCommands by inject()
    private val appScope: AppScope by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[COMMAND]?.let { name -> PlayerCommand.entries.firstOrNull { it.name == name } }
        if (command == null) {
            Log.w(TAG, "Widget action without a valid command: ${parameters[COMMAND]}")
            return
        }
        Log.d(TAG, "Widget tap: $command")
        // Return at once. Glance runs callbacks one after another, so waiting here for Spotify (which
        // can take many seconds through the companion app) would queue every later tap behind this one.
        appScope.launch { commands.run(command) }
    }

    companion object {
        val COMMAND = ActionParameters.Key<String>("command")
    }
}
