package com.doughindson.karootunes.ui.tracks

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.doughindson.karootunes.R
import com.doughindson.karootunes.data.spotify.ApiError
import com.doughindson.karootunes.data.spotify.ApiException
import com.doughindson.karootunes.data.spotify.ApiResult
import com.doughindson.karootunes.data.spotify.OffsetPagingSource
import com.doughindson.karootunes.data.spotify.SpotifyWebApi
import com.doughindson.karootunes.data.spotify.map
import com.doughindson.karootunes.data.spotify.model.Item
import com.doughindson.karootunes.data.spotify.model.Paging
import com.doughindson.karootunes.data.spotify.model.smallestUrl
import com.doughindson.karootunes.data.spotify.valueOrNull
import com.doughindson.karootunes.playback.PlayerCommands
import com.doughindson.karootunes.ui.TrackListRoute
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class TrackListKind { PLAYLIST, SHOW, SAVED_TRACKS, SAVED_EPISODES, QUEUE }

data class TrackItem(
    val uri: String,
    val name: String,
    val subtitle: String?,
    val imageUrl: String?,
    val isEpisode: Boolean,
)

sealed interface TrackListEvent {
    data object PlaybackStarted : TrackListEvent
    data class Done(@param:StringRes val message: Int) : TrackListEvent
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

    /** Lists that can be played as a whole from the header's play button. */
    val canPlayAll: Boolean = kind != TrackListKind.QUEUE

    // Liked Songs plays as the user's collection context, which needs their user ID.
    private val userId = if (kind == TrackListKind.SAVED_TRACKS) viewModelScope.async { api.currentUser().valueOrNull()?.id } else null

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

    private suspend fun contextUri(): String? = when (kind) {
        TrackListKind.PLAYLIST -> id?.let { "spotify:playlist:$it" }
        TrackListKind.SHOW -> id?.let { "spotify:show:$it" }
        TrackListKind.SAVED_TRACKS -> userId?.await()?.let(::likedSongsContextUri)
        else -> null
    }

    /** Plays [item]; without a context, the loaded items after it follow so playback doesn't stop after one. */
    fun playFrom(item: TrackItem, loaded: List<TrackItem>) = launch(startsPlayback = true) {
        val context = contextUri()
        if (context != null) {
            commands.playContext(context, item.uri)
        } else {
            val start = loaded.indexOfFirst { it.uri == item.uri }.coerceAtLeast(0)
            commands.playUris(loaded.drop(start).take(MAX_URIS).map { it.uri }.ifEmpty { listOf(item.uri) })
        }
    }

    /** The header's play button: the whole playlist, show or Liked Songs, or the loaded episodes. */
    fun playAll(loaded: List<TrackItem>) = launch(startsPlayback = true) {
        val context = contextUri()
        when {
            context != null -> commands.playContext(context)
            loaded.isNotEmpty() -> commands.playUris(loaded.take(MAX_URIS).map { it.uri })
            else -> ApiResult.Failure(ApiError.Unexpected("Nothing to play yet"))
        }
    }

    fun addToQueue(item: TrackItem) = launch(doneMessage = R.string.added_to_queue) { commands.addToQueue(listOf(item.uri)) }

    /** In Liked Songs every song is already liked, so the action there removes it instead. */
    val likeActionRemoves: Boolean = kind == TrackListKind.SAVED_TRACKS

    fun toggleLike(item: TrackItem) {
        val liked = !likeActionRemoves
        launch(doneMessage = if (liked) R.string.added_to_liked else R.string.removed_from_liked) {
            commands.setLiked(listOf(item.uri), liked).also { result ->
                // The cached Liked Songs pages would still show it; reload them.
                if (!liked && result is ApiResult.Ok) clearCache()
            }
        }
    }

    private fun launch(startsPlayback: Boolean = false, @StringRes doneMessage: Int? = null, block: suspend () -> ApiResult<Unit>) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                when (val result = block()) {
                    is ApiResult.Failure -> _events.send(TrackListEvent.Failed(result.error.message))
                    else -> when {
                        startsPlayback -> _events.send(TrackListEvent.PlaybackStarted)
                        doneMessage != null -> _events.send(TrackListEvent.Done(doneMessage))
                    }
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

/** Spotify's context for a user's Liked Songs. */
fun likedSongsContextUri(userId: String) = "spotify:user:$userId:collection"

/** "Mar 1 • 45 min" (this year) or "Mar 1, 2024 • 1 hr 5 min", as Spotify shows episodes. */
fun episodeSubtitle(releaseDate: String?, durationMs: Int?, today: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String? {
    val date = releaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.let { date ->
        val pattern = if (date.year == today.year) "MMM d" else "MMM d, yyyy"
        date.format(DateTimeFormatter.ofPattern(pattern, locale))
    }
    val duration = durationMs?.let { ms ->
        val minutes = ((ms + 30_000) / 60_000).coerceAtLeast(1)
        if (minutes < 60) "$minutes min" else "${minutes / 60} hr ${minutes % 60} min"
    }
    return listOfNotNull(date, duration).joinToString(" • ").ifEmpty { null }
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
        subtitle = if (isEpisode) episodeSubtitle(releaseDate, durationMs) else artistNames,
        imageUrl = (allImages ?: show?.images).smallestUrl(),
        isEpisode = isEpisode,
    )
}
