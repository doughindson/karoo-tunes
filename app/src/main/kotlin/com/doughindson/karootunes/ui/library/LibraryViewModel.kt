package com.doughindson.karootunes.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.doughindson.karootunes.data.spotify.OffsetPagingSource
import com.doughindson.karootunes.data.spotify.SpotifyWebApi
import com.doughindson.karootunes.data.spotify.model.smallestUrl
import kotlinx.coroutines.flow.Flow

/** A playlist or podcast show in the library lists. */
data class CollectionItem(val id: String, val name: String, val imageUrl: String?, val itemCount: Int?)

enum class CollectionKind { PLAYLISTS, SHOWS }

class LibraryViewModel(private val api: SpotifyWebApi) : ViewModel() {
    private val config = PagingConfig(pageSize = SpotifyWebApi.PAGE_SIZE, prefetchDistance = 10)

    val playlists: Flow<PagingData<CollectionItem>> = Pager(config) {
        OffsetPagingSource(fetch = api::playlists) { playlist ->
            playlist.id?.let { CollectionItem(it, playlist.name.orEmpty(), playlist.images.smallestUrl(), playlist.itemCount) }
        }
    }.flow.cachedIn(viewModelScope)

    val shows: Flow<PagingData<CollectionItem>> = Pager(config) {
        OffsetPagingSource(fetch = api::savedShows) { saved ->
            saved.show?.let { show -> show.id?.let { CollectionItem(it, show.name.orEmpty(), show.images.smallestUrl(), show.totalEpisodes) } }
        }
    }.flow.cachedIn(viewModelScope)

    /** Pull-to-refresh: forget cached pages so the reload hits Spotify. */
    fun clearCache(kind: CollectionKind) = api.clearCache(
        when (kind) {
            CollectionKind.PLAYLISTS -> SpotifyWebApi.PLAYLISTS
            CollectionKind.SHOWS -> SpotifyWebApi.SHOWS
        }
    )
}
