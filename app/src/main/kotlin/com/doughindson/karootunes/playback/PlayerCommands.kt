package com.doughindson.karootunes.playback

import android.util.Log
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.karoo.KarooGateway
import com.doughindson.karootunes.data.spotify.ApiResult

/**
 * Runs player commands: shows the expected result immediately, sends the command, reports failures
 * as an in-ride alert, and asks for a fresh state afterwards either way.
 */
class PlayerCommands(
    private val repository: PlayerRepository,
    private val controller: PlaybackController,
    private val widgetUi: WidgetUiStateHolder,
    private val karoo: KarooGateway,
) {
    suspend fun run(command: PlayerCommand) {
        if (command == PlayerCommand.TOGGLE_OPTIONS) {
            widgetUi.toggleOptions()
            return
        }
        val before = repository.state.value
        if (!PlayerReducer.isAllowed(before, command)) return
        if (!widgetUi.tryMarkPending(command)) return

        val now = repository.now()
        val restartsTrack = command == PlayerCommand.PREVIOUS && PlayerReducer.previousRestartsTrack(before, now)
        if (command == PlayerCommand.NEXT || command == PlayerCommand.PREVIOUS) widgetUi.closeOptions()
        repository.beginCommand()
        try {
            val after = repository.updateAndGet { PlayerReducer.optimistic(it, command, now) }
            val result = when (command) {
                PlayerCommand.PLAY -> controller.play()
                PlayerCommand.PAUSE -> controller.pause()
                PlayerCommand.NEXT -> controller.next()
                PlayerCommand.PREVIOUS -> if (restartsTrack) controller.seekTo(0) else controller.previous()
                PlayerCommand.SEEK_BACK, PlayerCommand.SEEK_FORWARD -> controller.seekTo(after.positionMs)
                PlayerCommand.TOGGLE_SHUFFLE -> controller.setShuffle(after.shuffle)
                PlayerCommand.CYCLE_REPEAT -> controller.setRepeat(after.repeat)
                PlayerCommand.TOGGLE_LIKE -> controller.setLiked(listOfNotNull(before.track?.uri), after.isLiked == true)
                PlayerCommand.TOGGLE_OPTIONS -> ApiResult.Ok(Unit)
            }
            if (result is ApiResult.Failure) {
                Log.w(TAG, "$command failed: ${result.error}")
                // Polls don't report liked state, so undo it here; other commands are corrected by the next poll.
                if (command == PlayerCommand.TOGGLE_LIKE) {
                    repository.updateAndGet { if (it.track?.uri == before.track?.uri) it.copy(isLiked = before.isLiked) else it }
                }
                karoo.showAlert(command.alertTitle(), result.error.message)
            }
        } finally {
            repository.endCommand()
            widgetUi.clearPending(command)
            repository.requestRefresh()
        }
    }

    /** Starts playback from the app's library screens. */
    suspend fun playContext(contextUri: String, startAtUri: String? = null) =
        controller.playContext(contextUri, startAtUri).also { repository.requestRefresh() }

    suspend fun playUris(uris: List<String>) = controller.playUris(uris).also { repository.requestRefresh() }

    suspend fun addToQueue(uris: List<String>): ApiResult<Unit> {
        for (uri in uris) {
            val result = controller.addToQueue(uri)
            if (result is ApiResult.Failure) return result
        }
        repository.requestRefresh()
        return ApiResult.Ok(Unit)
    }

    suspend fun setLiked(uris: List<String>, liked: Boolean) = controller.setLiked(uris, liked).also { repository.requestRefresh() }

    private fun PlayerCommand.alertTitle() = when (this) {
        PlayerCommand.PLAY -> "Play"
        PlayerCommand.PAUSE -> "Pause"
        PlayerCommand.NEXT, PlayerCommand.PREVIOUS -> "Change track"
        PlayerCommand.SEEK_BACK, PlayerCommand.SEEK_FORWARD -> "Seek"
        PlayerCommand.TOGGLE_SHUFFLE -> "Shuffle"
        PlayerCommand.CYCLE_REPEAT -> "Repeat"
        PlayerCommand.TOGGLE_LIKE -> "Liked Songs"
        PlayerCommand.TOGGLE_OPTIONS -> "Options"
    }
}
