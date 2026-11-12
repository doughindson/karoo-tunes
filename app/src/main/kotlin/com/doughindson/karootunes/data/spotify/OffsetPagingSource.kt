package com.doughindson.karootunes.data.spotify

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.doughindson.karootunes.data.spotify.model.Paging
import kotlin.coroutines.cancellation.CancellationException

/**
 * Pages through any Spotify offset/limit endpoint. Keys are item offsets. [fetch] returns the raw
 * page; [transform] maps it to the list items (dropping entries Spotify returns as null).
 */
class OffsetPagingSource<Raw, T : Any>(
    private val pageSize: Int = SpotifyWebApi.PAGE_SIZE,
    private val fetch: suspend (offset: Int, limit: Int) -> ApiResult<Paging<Raw>>,
    private val transform: (Raw) -> T?,
) : PagingSource<Int, T>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> {
        val offset = params.key ?: 0
        return try {
            val page = fetch(offset, pageSize).getOrThrow { Paging() }
            val received = page.items.size
            val hasMore = if (page.next != null || page.total != null) {
                page.next != null && received > 0
            } else {
                received >= pageSize
            }
            LoadResult.Page(
                data = page.items.mapNotNull(transform),
                prevKey = null,
                nextKey = if (hasMore) offset + received else null,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    // Always reload from the start; lists are short and cached.
    override fun getRefreshKey(state: PagingState<Int, T>): Int? = null
}
