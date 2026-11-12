package com.doughindson.karootunes.ui.tracks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.doughindson.karootunes.data.spotify.ApiError
import com.doughindson.karootunes.data.spotify.ApiException
import com.doughindson.karootunes.data.spotify.ApiResult
import com.doughindson.karootunes.data.spotify.OffsetPagingSource
import com.doughindson.karootunes.data.spotify.SpotifyWebApi
import com.doughindson.karootunes.data.spotify.map
import com.doughindson.karootunes.data.spotify.model.Item
import com.doughindson.karootunes.data.spotify.model.Paging
import com.doughindson.karootunes.data.spotify.model.smallestUrl
import com.doughindson.karootunes.playback.PlayerCommands
import com.doughindson.karootunes.ui.TrackListRoute
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

enum class TrackListKind { PLAYLIST, SHOW, SAVED_TRACKS, SAVED_EPISODES, QUEUE }

data class TrackItem(
    val uri: String,
    val name: String,
    val subtitle: String?,
    val durationMs: Int?,
    val imageUrl: String?,
    val isEpisode: Boolean,
)

sealed interface TrackListEvent {
    data object PlaybackStarted : TrackListEvent
    data class Failed(val message: String) : TrackListEvent
}

class TrackListViewModel(
    route: TrackListRoute,
    private val api: SpotifyWebApi,
    private val commands: PlayerCommands,
) : ViewModel() {
    val kind: TrackListKind = TrackListKind.valueOf(route.kind)
    val id: String? = route.id
    val title: String? = route.title

    /** Playlists and shows can be played as a whole, so playback continues past the visible items. */
    val contextUri: String? = when (kind) {
        TrackListKind.PLAYLIST -> id?.let { "spotify:playlist:$it" }
        TrackListKind.SHOW -> id?.let { "spotify:show:$it" }
        else -> null
    }

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<TrackListEvent>(Channel.BUFFERED)
    val events: Flow<TrackListEvent> = _events.receiveAsFlow()

    val items: Flow<PagingData<TrackItem>> = Pager(PagingConfig(pageSize = SpotifyWebApi.PAGE_SIZE, prefetchDistance = 10)) {
        when (kind) {
            TrackListKind.PLAYLIST -> OffsetPagingSource(fetch = { o, l -> api.playlistItems(id!!, o, l) }) { it.content?.toTrackItem() }
            TrackListKind.SHOW -> OffsetPagingSource(fetch = { o, l -> api.showEpisodes(id!!, o, l) }) { it.toTrackItem() }
            TrackListKind.SAVED_TRACKS -> OffsetPagingSource(fetch = api::savedTracks) { it.track?.toTrackItem() }
            TrackListKind.SAVED_EPISODES -> OffsetPagingSource(fetch = api::savedEpisodes) { it.episode?.toTrackItem() }
            TrackListKind.QUEUE -> OffsetPagingSource<Item, TrackItem>(
                fetch = { offset, _ ->
                    if (offset > 0) {
                        ApiResult.Ok(Paging())
                    } else {
                        api.queue().map { Paging(items = listOfNotNull(it.currentlyPlaying) + it.queue) }
                    }
                },
            ) { it.toTrackItem() }
        }
    }.flow.cachedIn(viewModelScope)

    fun clearCache() {
        when (kind) {
            TrackListKind.PLAYLIST -> api.clearCache(SpotifyWebApi.playlistPrefix(id!!))
            TrackListKind.SHOW -> api.clearCache(SpotifyWebApi.showPrefix(id!!))
            TrackListKind.SAVED_TRACKS -> api.clearCache(SpotifyWebApi.SAVED_TRACKS)
            TrackListKind.SAVED_EPISODES -> api.clearCache(SpotifyWebApi.SAVED_EPISODES)
            TrackListKind.QUEUE -> Unit
        }
    }

    /** Plays [item]; without a context, the loaded items after it follow so playback doesn't stop after one. */
    fun playFrom(item: TrackItem, loaded: List<TrackItem>) = launchCommand(startsPlayback = true) {
        if (contextUri != null) {
            commands.playContext(contextUri, item.uri)
        } else {
            val start = loaded.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
            commands.playUris(loaded.drop(start).take(MAX_URIS).map { it.uri }.ifEmpty { listOf(item.uri) })
        }
    }

    fun playAll() = launchCommand(startsPlayback = true) { commands.playContext(contextUri!!) }

    fun playSelection(selected: List<TrackItem>) = launchCommand(startsPlayback = true) { commands.playUris(selected.map { it.uri }) }

    fun addToQueue(selected: List<TrackItem>) = launchCommand(startsPlayback = true) { commands.addToQueue(selected.map { it.uri }) }

    fun like(selected: List<TrackItem>) = launchCommand(startsPlayback = false) { commands.setLiked(selected.map { it.uri }, liked = true) }

    private fun launchCommand(startsPlayback: Boolean, block: suspend () -> ApiResult<Unit>) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val result = block()) {
                    is ApiResult.Failure -> _events.send(TrackListEvent.Failed(result.error.message))
                    else -> if (startsPlayback) _events.send(TrackListEvent.PlaybackStarted)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private companion object {
        /** Spotify accepts a limited number of URIs per play request. */
        const val MAX_URIS = 50
    }
}

/** Spotify refuses to list items of playlists the user neither owns nor collaborates on. */
fun Throwable.isPlaylistAccessDenied(): Boolean =
    (this as? ApiException)?.error.let { it is ApiError.Http && (it.status == 403 || it.status == 404) }

private fun Item.toTrackItem(): TrackItem? {
    val uri = uri ?: return null
    if (isLocal == true) return null // local files can't be played remotely
    return TrackItem(
        uri = uri,
        name = name.orEmpty(),
        subtitle = if (isEpisode) releaseDate else artistNames,
        durationMs = durationMs,
        imageUrl = (allImages ?: show?.images).smallestUrl(),
        isEpisode = isEpisode,
    )
}
