package com.doughindson.karootunes.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.playback.PlayerCommand
import com.doughindson.karootunes.playback.PlayerCommands
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** The one click handler for every widget button; the button's [PlayerCommand] arrives as a parameter. */
class PlayerActionCallback : ActionCallback, KoinComponent {
    private val commands: PlayerCommands by inject()

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[COMMAND]?.let { name -> PlayerCommand.entries.firstOrNull { it.name == name } }
        if (command == null) {
            Log.w(TAG, "Widget action without a valid command: ${parameters[COMMAND]}")
            return
        }
        commands.run(command)
    }

    companion object {
        val COMMAND = ActionParameters.Key<String>("command")
    }
}
