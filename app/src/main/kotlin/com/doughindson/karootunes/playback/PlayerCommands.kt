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
        val before = repository.state.value
        if (!PlayerReducer.isAllowed(before, command)) {
            // Our state may be stale (e.g. Spotify was just restarted on the phone): check now.
            if (!before.hasPlayer) repository.requestRefresh()
            return
        }
        if (!widgetUi.tryMarkPending(command)) return

        val now = repository.now()
        val restartsTrack = command == PlayerCommand.PREVIOUS && PlayerReducer.previousRestartsTrack(before, now)
        repository.beginCommand()
        try {
            val after = repository.updateAndGet { PlayerReducer.optimistic(it, command, now) }
            val result = when (command) {
                PlayerCommand.PLAY -> playOnKnownDevice(before)
                PlayerCommand.PAUSE -> controller.pause()
                PlayerCommand.NEXT -> controller.next()
                PlayerCommand.PREVIOUS -> if (restartsTrack) controller.seekTo(0) else controller.previous()
                PlayerCommand.SEEK_BACK, PlayerCommand.SEEK_FORWARD -> controller.seekTo(after.positionMs)
                PlayerCommand.TOGGLE_SHUFFLE -> controller.setShuffle(after.shuffle)
                PlayerCommand.CYCLE_REPEAT -> controller.setRepeat(after.repeat)
                PlayerCommand.TOGGLE_LIKE -> toggleLikeOnPlayingTrack(before, after, now)
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
                if (command == PlayerCommand.PLAY && before.lastDeviceId != null && result.error.isDeviceGone()) {
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
     * Likes the song that is actually playing. The rider may have skipped with the Karoo's own media
     * controls since our last refresh, so check first; if the song changed, show it and like it.
     */
    private suspend fun toggleLikeOnPlayingTrack(before: PlayerState, after: PlayerState, now: Long): ApiResult<Unit> {
        val playing = (controller.snapshot() as? ApiResult.Ok)?.value
        val playingUri = playing?.track?.uri
        if (playing != null && playingUri != null && playingUri != before.track?.uri) {
            Log.d(TAG, "Track changed since the last refresh; liking the one now playing")
            repository.updateAndGet { PlayerReducer.applySnapshot(it, playing, now).copy(isLiked = true) }
            return controller.setLiked(listOf(playingUri), liked = true)
        }
        return controller.setLiked(listOfNotNull(before.track?.uri), after.isLiked == true)
    }

    /**
     * Resumes playback. Spotify marks a paused phone inactive after a few seconds, and a plain Play
     * then fails with "No active device"; naming the last device can still reach it, so retry once.
     */
    private suspend fun playOnKnownDevice(before: PlayerState): ApiResult<Unit> {
        val lastDevice = before.lastDeviceId
        if (!before.hasPlayer && lastDevice != null) return controller.play(deviceId = lastDevice)
        val result = controller.play()
        if (lastDevice != null && result is ApiResult.Failure && result.error.isDeviceGone()) {
            Log.d(TAG, "No active device; retrying play on the last device")
            return controller.play(deviceId = lastDevice)
        }
        return result
    }

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
    }
}
