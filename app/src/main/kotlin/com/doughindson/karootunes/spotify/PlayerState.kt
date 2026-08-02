package com.doughindson.karootunes.spotify

data class CachedTrackInfo(
    val trackId: String? = null,
    val trackUri: String? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val thumbnailUrls: List<String>? = null,
    val durationMs: Int? = null
)

data class PlayerState(
    val isPlayingType: PlaybackType? = null,
    val isPlayingTrackName: String? = null,
    val isPlayingArtistName: String? = null,
    val isPlayingShowName: String? = null,
    val isShuffling: Boolean? = null,
    val isPlaying: Boolean? = null,
    val isLocalPlayer: Boolean = false,
    val isRepeating: RepeatState? = null,
    val isInitialized: Boolean = false,
    val isInOptionsMenu: Boolean = false,
    val isPlayingTrackId: String? = null,
    val isPlayingTrackUri: String? = null,
    val isPlayingTrackThumbnailUrls: List<String>? = null,
    val commandPending: Boolean = false,
    val requestPending: Int = 0,
    val playProgressInMs: Int? = null,
    val currentTrackLengthInMs: Int? = null,
    val volume: Float? = null,
    val thumbnailFetchedAtMs: Long? = null,
    val isTrackLiked: Boolean? = null,

    // Prefetched so Next/Previous can show real track info the instant the button is pressed,
    // instead of a generic loading state, while the actual skip request is still in flight.
    val previousTrackCache: CachedTrackInfo? = null,
    val upcomingTracksCache: List<CachedTrackInfo> = emptyList(),

    val disabledActions: MutableMap<PlayerAction, Boolean> = mutableMapOf(),
    val pendingActions: Set<PlayerAction> = emptySet(),

    val error: PlayerError? = null
)

data class PlayerError(val title: String, val message: String)

enum class PlayerAction {
    PLAY, PAUSE, SKIP_NEXT, SKIP_PREVIOUS, SEEK, TOGGLE_SHUFFLE, TOGGLE_REPEAT, SET_VOLUME, ADD_TO_LIBRARY
}