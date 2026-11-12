package com.doughindson.karootunes.playback

/** Pure state transitions, kept free of Android and I/O so they can be unit-tested. */
object PlayerReducer {
    const val SEEK_BACK_MS = 15_000
    const val SEEK_FORWARD_MS = 30_000
    private const val RESTART_THRESHOLD_MS = 15_000
    private const val END_OF_TRACK_MARGIN_MS = 2_000
    private const val UPCOMING_LIMIT = 3

    fun applySnapshot(state: PlayerState, snapshot: PlaybackSnapshot, now: Long): PlayerState {
        val trackChanged = snapshot.track?.uri != state.track?.uri
        return state.copy(
            connection = Connection.ACTIVE,
            track = snapshot.track,
            isPlaying = snapshot.isPlaying,
            positionMs = snapshot.positionMs,
            positionUpdatedAt = now,
            shuffle = snapshot.shuffle,
            repeat = snapshot.repeat,
            disallowed = snapshot.disallowed,
            lastDeviceId = snapshot.deviceId ?: state.lastDeviceId,
            lastDeviceName = snapshot.deviceName ?: state.lastDeviceName,
            isLiked = if (trackChanged) null else state.isLiked,
            previousTrack = if (trackChanged && state.track != null) state.track else state.previousTrack,
            upcoming = if (trackChanged) state.upcoming.dropWhile { it.uri == snapshot.track?.uri } else state.upcoming,
            error = null,
        )
    }

    /**
     * Spotify reports no active device. Keep showing the last track, paused, so Play can resume it on
     * the last device; without one, ask the rider to start Spotify on the phone.
     */
    fun noPlayer(state: PlayerState): PlayerState = state.copy(
        connection = Connection.NO_PLAYER,
        isPlaying = false,
        disallowed = emptySet(),
        error = if (state.lastDeviceId != null) {
            PlayerError("Paused", "Press play to resume on ${state.lastDeviceName ?: "your phone"}")
        } else {
            PlayerError("No active player", "Start playing Spotify on your phone")
        },
    )

    fun notLoggedIn(): PlayerState = PlayerState(
        connection = Connection.NOT_LOGGED_IN,
        error = PlayerError("Not logged in", "Open Karoo Tunes to log in to Spotify"),
    )

    /** A refresh failed: keep showing what we had, plus the reason. */
    fun refreshFailed(state: PlayerState, error: PlayerError): PlayerState = state.copy(error = error)

    /** On Previous, Spotify restarts the track unless we're near its start (or its end). We mirror that. */
    fun previousRestartsTrack(state: PlayerState, now: Long): Boolean {
        val position = state.positionAt(now)
        val duration = state.track?.durationMs
        val nearEnd = duration != null && position >= duration - END_OF_TRACK_MARGIN_MS
        return PlayerCommand.PREVIOUS in state.disallowed || (position >= RESTART_THRESHOLD_MS && !nearEnd)
    }

    fun isAllowed(state: PlayerState, command: PlayerCommand): Boolean = when (command) {
        PlayerCommand.TOGGLE_OPTIONS -> true
        PlayerCommand.TOGGLE_LIKE -> state.hasPlayer && state.track?.uri != null
        PlayerCommand.PLAY -> (state.hasPlayer || state.canResumeIdle) && command !in state.disallowed
        else -> state.hasPlayer && command !in state.disallowed
    }

    /** The state the user should see immediately after issuing [command]. */
    fun optimistic(state: PlayerState, command: PlayerCommand, now: Long): PlayerState {
        val position = state.positionAt(now)
        return when (command) {
            PlayerCommand.PLAY -> state.copy(isPlaying = true, positionMs = position, positionUpdatedAt = now, error = null)
            PlayerCommand.PAUSE -> state.copy(isPlaying = false, positionMs = position, positionUpdatedAt = now)
            PlayerCommand.NEXT -> state.copy(
                track = state.upcoming.firstOrNull(),
                upcoming = state.upcoming.drop(1),
                previousTrack = state.track ?: state.previousTrack,
                positionMs = 0,
                positionUpdatedAt = now,
                isLiked = null,
            )
            PlayerCommand.PREVIOUS ->
                if (previousRestartsTrack(state, now)) {
                    state.copy(positionMs = 0, positionUpdatedAt = now)
                } else {
                    state.copy(
                        track = state.previousTrack,
                        previousTrack = null,
                        upcoming = (listOfNotNull(state.track) + state.upcoming).take(UPCOMING_LIMIT),
                        positionMs = 0,
                        positionUpdatedAt = now,
                        isLiked = null,
                    )
                }
            PlayerCommand.SEEK_BACK -> state.copy(positionMs = (position - SEEK_BACK_MS).coerceAtLeast(0), positionUpdatedAt = now)
            PlayerCommand.SEEK_FORWARD -> {
                val target = position + SEEK_FORWARD_MS
                state.copy(positionMs = state.track?.durationMs?.let { target.coerceAtMost(it) } ?: target, positionUpdatedAt = now)
            }
            PlayerCommand.TOGGLE_SHUFFLE -> state.copy(shuffle = !state.shuffle)
            PlayerCommand.CYCLE_REPEAT -> state.copy(repeat = state.repeat.next())
            PlayerCommand.TOGGLE_LIKE -> state.copy(isLiked = state.isLiked != true)
            PlayerCommand.TOGGLE_OPTIONS -> state
        }
    }
}
