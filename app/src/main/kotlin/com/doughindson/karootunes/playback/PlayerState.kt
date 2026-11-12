package com.doughindson.karootunes.playback

enum class MediaType { TRACK, EPISODE, AD, UNKNOWN }

enum class RepeatMode(val apiValue: String) {
    OFF("off"), CONTEXT("context"), TRACK("track");

    fun next(): RepeatMode = when (this) {
        OFF -> CONTEXT
        CONTEXT -> TRACK
        TRACK -> OFF
    }

    companion object {
        fun fromApi(value: String?): RepeatMode = entries.firstOrNull { it.apiValue == value } ?: OFF
    }
}

/** Everything the player shows about one track or episode. Image URLs are ordered largest first. */
data class Track(
    val uri: String?,
    val name: String?,
    /** Artists for a track, the show name for an episode. */
    val subtitle: String?,
    val durationMs: Int?,
    val type: MediaType,
    val smallImageUrl: String? = null,
    val mediumImageUrl: String? = null,
) {
    fun imageUrl(highRes: Boolean): String? = if (highRes) mediumImageUrl ?: smallImageUrl else smallImageUrl
}

/** Commands a user can issue from the widget. */
enum class PlayerCommand {
    PLAY, PAUSE, NEXT, PREVIOUS, SEEK_BACK, SEEK_FORWARD, TOGGLE_SHUFFLE, CYCLE_REPEAT, TOGGLE_LIKE, TOGGLE_OPTIONS
}

/** What Spotify last reported. */
data class PlaybackSnapshot(
    val track: Track?,
    val isPlaying: Boolean,
    val positionMs: Int,
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val disallowed: Set<PlayerCommand>,
    val deviceId: String? = null,
    val deviceName: String? = null,
)

enum class Connection {
    /** Nothing fetched yet. */
    UNKNOWN,
    ACTIVE,
    /**
     * Logged in, but Spotify has no active device. This happens when Spotify on the phone has been
     * paused for a while. Play can still resume on [PlayerState.lastDeviceId].
     */
    NO_PLAYER,
    NOT_LOGGED_IN,
}

/** A problem with the last refresh. The last good playback state is kept alongside it. */
data class PlayerError(val title: String, val message: String)

data class PlayerState(
    val connection: Connection = Connection.UNKNOWN,
    val track: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Int = 0,
    /** Clock time (see [PlayerRepository]'s clock) at which [positionMs] was measured. */
    val positionUpdatedAt: Long = 0,
    val shuffle: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    /** Null while unknown (just changed track, or lookup failed). */
    val isLiked: Boolean? = null,
    val disallowed: Set<PlayerCommand> = emptySet(),
    /** Shown instantly on Previous while the real skip is in flight. */
    val previousTrack: Track? = null,
    /**
     * Shown instantly on Next while the real skip is in flight. Only filled by going back with
     * Previous: fetching Spotify's queue in the background is too slow through the companion app,
     * where it blocks button presses for many seconds.
     */
    val upcoming: List<Track> = emptyList(),
    val error: PlayerError? = null,
    /** The device Spotify last played on, so Play can resume there after Spotify goes idle. */
    val lastDeviceId: String? = null,
    val lastDeviceName: String? = null,
) {
    val hasPlayer: Boolean get() = connection == Connection.ACTIVE

    /** Spotify went idle, but we know where to resume. */
    val canResumeIdle: Boolean get() = connection == Connection.NO_PLAYER && lastDeviceId != null

    /** Playback position extrapolated to [now] while playing. */
    fun positionAt(now: Long): Int {
        if (!isPlaying) return positionMs
        val advanced = positionMs + (now - positionUpdatedAt).coerceAtLeast(0).toInt()
        return track?.durationMs?.let { advanced.coerceAtMost(it) } ?: advanced
    }
}
