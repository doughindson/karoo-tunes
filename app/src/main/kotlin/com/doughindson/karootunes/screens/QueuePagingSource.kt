package com.doughindson.karootunes.screens

import android.content.Context
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.doughindson.karootunes.spotify.APIClient
import com.doughindson.karootunes.spotify.LocalClient
import com.doughindson.karootunes.spotify.WebAPIClient
import com.doughindson.karootunes.spotify.model.TrackObject
import com.doughindson.karootunes.spotify.model.Item
import com.doughindson.karootunes.spotify.model.ItemWrapper

class QueuePagingSource(
    val ctx: Context, val apiClient: APIClient, val webAPIClient: WebAPIClient
): PagingSource<Int, TrackObject>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TrackObject> {
        return try {
            // Queue list is not provided with the local client
            val response = webAPIClient.getPlayerQueue()
            val currentlyPlaying = if (apiClient is WebAPIClient){
                listOfNotNull(response?.currentlyPlaying).map { ItemWrapper(it) }
            } else if (apiClient is LocalClient){
                val item = apiClient.getPlayerState()?.track?.let { Item.fromSpotifyTrack(it) }

                if (item != null){
                    listOf(ItemWrapper(item))
                } else {
                    emptyList()
                }
            } else {
                error("Unknown API client")
            }
            val queue = currentlyPlaying + (response?.queue?.map { ItemWrapper(it) } ?: emptyList())

            LoadResult.Page(
                data = queue,
                prevKey = null,
                nextKey = null
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, TrackObject>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}