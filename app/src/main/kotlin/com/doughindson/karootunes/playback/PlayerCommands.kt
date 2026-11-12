package com.doughindson.karootunes.playback

import android.util.Log
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.karoo.KarooGateway
import com.doughindson.karootunes.data.spotify.ApiError
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
        if (!PlayerReducer.isAllowed(before, command)) {
            // Our state may be stale (e.g. Spotify was just restarted on the phone): check now.
            if (!before.hasPlayer) repository.requestRefresh()
            return
        }
        if (!widgetUi.tryMarkPending(command)) return

        val now = repository.now()
        val restartsTrack = command == PlayerCommand.PREVIOUS && PlayerReducer.previousRestartsTrack(before, now)
        if (command == PlayerCommand.NEXT || command == PlayerCommand.PREVIOUS) widgetUi.closeOptions()
        repository.beginCommand()
        try {
            val after = repository.updateAndGet { PlayerReducer.optimistic(it, command, now) }
            val result = when (command) {
                PlayerCommand.PLAY -> controller.play(deviceId = if (before.hasPlayer) null else before.lastDeviceId)
                PlayerCommand.PAUSE -> controller.pause()
                PlayerCommand.NEXT -> controller.next()
                PlayerCommand.PREVIOUS -> if (restartsTrack) controller.seekTo(0) else controller.previous()
                PlayerCommand.SEEK_BACK, PlayerCommand.SEEK_FORWARD -> controller.seekTo(after.positionMs)
                PlayerCommand.TOGGLE_SHUFFLE -> controller.setShuffle(after.shuffle)
                PlayerCommand.CYCLE_REPEAT -> controller.setRepeat(after.repeat)
                PlayerCommand.TOGGLE_LIKE -> controller.setLiked(listOfNotNull(before.track?.uri), after.isLiked == true)
                PlayerCommand.TOGGLE_OPTIONS -> ApiResult.Ok(Unit)
            }
            if (result is ApiResult.Ok && (command == PlayerCommand.PLAY || command == PlayerCommand.PAUSE)) {
                repository.expectPlaying(command == PlayerCommand.PLAY)
            }
            if (result is ApiResult.Failure) {
                Log.w(TAG, "$command failed: ${result.error}")
                // Polls don't report liked state, so undo it here; other commands are corrected by the next poll.
                if (command == PlayerCommand.TOGGLE_LIKE) {
                    repository.updateAndGet { if (it.track?.uri == before.track?.uri) it.copy(isLiked = before.isLiked) else it }
                }
                val resumingIdle = command == PlayerCommand.PLAY && !before.hasPlayer
                if (resumingIdle && result.error.isDeviceGone()) {
                    repository.updateAndGet { PlayerReducer.resumeFailed(it) }
                    karoo.showAlert(
                        command.alertTitle(),
                        "Spotify on ${before.lastDeviceName ?: "your phone"} has gone to sleep. Start playing on the phone; the Karoo can control it again from there.",
                    )
                } else {
                    alertFor(command, result.error)?.let { (title, message) -> karoo.showAlert(title, message) }
                }
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

    /**
     * What to tell the rider about a failed command, or null when it isn't worth an alert. Spotify
     * answers 403 "Restriction violated" when play/pause doesn't match its real state (e.g. pausing
     * music that already stopped); the refresh that follows every command shows the real state.
     */
    private fun alertFor(command: PlayerCommand, error: ApiError): Pair<String, String>? {
        if (error is ApiError.Http) {
            val stateMismatch = error.status == 403 && error.reason != "PREMIUM_REQUIRED" &&
                (command == PlayerCommand.PLAY || command == PlayerCommand.PAUSE)
            if (stateMismatch) return null
            if (error.isDeviceGone()) {
                return command.alertTitle() to "Spotify isn't running on your phone. Open Spotify there and start playing."
            }
            if (error.reason == "PREMIUM_REQUIRED") {
                return command.alertTitle() to "Controlling playback needs Spotify Premium."
            }
        }
        return command.alertTitle() to error.message
    }

    private fun ApiError.isDeviceGone() = this is ApiError.Http && (status == 404 || reason == "NO_ACTIVE_DEVICE")

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
