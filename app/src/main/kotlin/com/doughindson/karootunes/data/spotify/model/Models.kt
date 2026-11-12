package com.doughindson.karootunes.data.spotify.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Only the fields the app uses. Everything is optional: Spotify omits fields freely, and the
// February 2026 Development Mode changes removed several (popularity, available_markets, …).

@Serializable
data class Image(val url: String? = null, val width: Int? = null, val height: Int? = null)

/** Spotify lists images largest first. */
fun List<Image>?.smallestUrl(): String? = this?.lastOrNull { it.url != null }?.url

/** The ~300px image when available, which is a good fit for the full-page album art. */
fun List<Image>?.mediumUrl(): String? = this?.filter { it.url != null }?.let { it.getOrNull(1) ?: it.lastOrNull() }?.url

@Serializable
data class Artist(val id: String? = null, val name: String? = null, val uri: String? = null)

@Serializable
data class Album(val id: String? = null, val name: String? = null, val uri: String? = null, val images: List<Image>? = null)

@Serializable
data class Show(
    val id: String? = null,
    val name: String? = null,
    val uri: String? = null,
    val images: List<Image>? = null,
    @SerialName("total_episodes") val totalEpisodes: Int? = null,
)

/** A track or a podcast episode. */
@Serializable
data class Item(
    val id: String? = null,
    val uri: String? = null,
    val name: String? = null,
    val type: String? = null,
    @SerialName("duration_ms") val durationMs: Int? = null,
    val artists: List<Artist>? = null,
    val album: Album? = null,
    val images: List<Image>? = null,
    val show: Show? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("is_local") val isLocal: Boolean? = null,
) {
    val isEpisode: Boolean get() = type == "episode"
    val allImages: List<Image>? get() = images?.takeIf { it.isNotEmpty() } ?: album?.images
    val artistNames: String? get() = artists?.mapNotNull { it.name }?.takeIf { it.isNotEmpty() }?.joinToString(", ")
}

@Serializable
data class Paging<T>(
    val items: List<T> = emptyList(),
    val next: String? = null,
    val offset: Int? = null,
    val limit: Int? = null,
    val total: Int? = null,
)

@Serializable
data class ItemCount(val total: Int? = null)

@Serializable
data class User(val id: String? = null, @SerialName("display_name") val displayName: String? = null)

@Serializable
data class Playlist(
    val id: String? = null,
    val name: String? = null,
    val uri: String? = null,
    val images: List<Image>? = null,
    val owner: User? = null,
    /** Renamed from `tracks` in 2026, and only present for playlists the user owns or collaborates on. */
    val items: ItemCount? = null,
    val tracks: ItemCount? = null,
) {
    val itemCount: Int? get() = (items ?: tracks)?.total
}

@Serializable
data class PlaylistItem(
    /** Called `track` before 2026. */
    val item: Item? = null,
    val track: Item? = null,
) {
    val content: Item? get() = item ?: track
}

@Serializable
data class SavedTrack(@SerialName("added_at") val addedAt: String? = null, val track: Item? = null)

@Serializable
data class SavedShow(val show: Show? = null)

@Serializable
data class SavedEpisode(val episode: Item? = null)

@Serializable
data class QueueResponse(
    @SerialName("currently_playing") val currentlyPlaying: Item? = null,
    val queue: List<Item> = emptyList(),
)

@Serializable
data class Device(
    val id: String? = null,
    val name: String? = null,
    @SerialName("volume_percent") val volumePercent: Int? = null,
    @SerialName("supports_volume") val supportsVolume: Boolean? = null,
)

@Serializable
data class Disallows(
    val pausing: Boolean? = null,
    val resuming: Boolean? = null,
    val seeking: Boolean? = null,
    @SerialName("skipping_next") val skippingNext: Boolean? = null,
    @SerialName("skipping_prev") val skippingPrev: Boolean? = null,
    @SerialName("toggling_repeat_context") val togglingRepeatContext: Boolean? = null,
    @SerialName("toggling_repeat_track") val togglingRepeatTrack: Boolean? = null,
    @SerialName("toggling_shuffle") val togglingShuffle: Boolean? = null,
)

@Serializable
data class Actions(val disallows: Disallows? = null)

@Serializable
data class PlaybackStateResponse(
    val device: Device? = null,
    @SerialName("repeat_state") val repeatState: String? = null,
    @SerialName("shuffle_state") val shuffleState: Boolean? = null,
    val timestamp: Long? = null,
    @SerialName("progress_ms") val progressMs: Int? = null,
    @SerialName("is_playing") val isPlaying: Boolean? = null,
    val item: Item? = null,
    @SerialName("currently_playing_type") val currentlyPlayingType: String? = null,
    val actions: Actions? = null,
)

@Serializable
data class Offset(val position: Int? = null, val uri: String? = null)

@Serializable
data class PlayRequest(
    @SerialName("context_uri") val contextUri: String? = null,
    val offset: Offset? = null,
    val uris: List<String>? = null,
)

@Serializable
data class WebApiError(val status: Int? = null, val message: String? = null)

@Serializable
data class WebApiErrorResponse(val error: WebApiError? = null)
