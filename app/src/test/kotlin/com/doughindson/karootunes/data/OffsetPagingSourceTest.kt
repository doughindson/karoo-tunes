package com.doughindson.karootunes.data

import androidx.paging.PagingSource
import com.doughindson.karootunes.data.spotify.ApiError
import com.doughindson.karootunes.data.spotify.ApiResult
import com.doughindson.karootunes.data.spotify.OffsetPagingSource
import com.doughindson.karootunes.data.spotify.model.Paging
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffsetPagingSourceTest {
    private fun refresh(key: Int? = null) = PagingSource.LoadParams.Refresh(key, loadSize = 30, placeholdersEnabled = false)
    private fun append(key: Int) = PagingSource.LoadParams.Append(key, loadSize = 30, placeholdersEnabled = false)

    @Test
    fun `next page starts after the items received`() = runTest {
        val offsets = mutableListOf<Int>()
        val source = OffsetPagingSource<String, String>(pageSize = 30, fetch = { offset, _ ->
            offsets += offset
            ApiResult.Ok(Paging(items = List(30) { "item${offset + it}" }, next = "more", total = 75))
        }) { it }

        val first = source.load(refresh()) as PagingSource.LoadResult.Page
        assertEquals(30, first.nextKey)
        val second = source.load(append(30)) as PagingSource.LoadResult.Page
        assertEquals(60, second.nextKey)
        assertEquals(listOf(0, 30), offsets)
    }

    @Test
    fun `a page smaller than the page size still continues while Spotify says there is more`() = runTest {
        // The old library paging compared against 50 with a page size of 30 and stopped after one page.
        val source = OffsetPagingSource<String, String>(pageSize = 30, fetch = { _, _ ->
            ApiResult.Ok(Paging(items = List(20) { "x" }, next = "more"))
        }) { it }
        assertEquals(20, (source.load(refresh()) as PagingSource.LoadResult.Page).nextKey)
    }

    @Test
    fun `last page has no next key`() = runTest {
        val source = OffsetPagingSource<String, String>(fetch = { _, _ -> ApiResult.Ok(Paging(items = listOf("a"), next = null, total = 1)) }) { it }
        assertNull((source.load(refresh()) as PagingSource.LoadResult.Page).nextKey)
    }

    @Test
    fun `null items are dropped by the transform`() = runTest {
        val source = OffsetPagingSource<String?, String>(fetch = { _, _ -> ApiResult.Ok(Paging(items = listOf("a", null, "b"))) }) { it }
        assertEquals(listOf("a", "b"), (source.load(refresh()) as PagingSource.LoadResult.Page).data)
    }

    @Test
    fun `failures become load errors`() = runTest {
        val source = OffsetPagingSource<String, String>(fetch = { _, _ -> ApiResult.Failure(ApiError.Offline("No connection")) }) { it }
        val result = source.load(refresh())
        assertTrue(result is PagingSource.LoadResult.Error)
        assertEquals("No connection", (result as PagingSource.LoadResult.Error).throwable.message)
    }
}
