package com.doughindson.karootunes.data

import com.doughindson.karootunes.data.auth.SpotifyAuth
import com.doughindson.karootunes.data.auth.StoredToken
import com.doughindson.karootunes.data.spotify.ApiError
import com.doughindson.karootunes.data.spotify.ApiResult
import com.doughindson.karootunes.data.spotify.ResponseCache
import com.doughindson.karootunes.data.spotify.SpotifyWebApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SpotifyWebApiTest {
    private val transport = FakeTransport()
    private val store = InMemoryTokenStore(StoredToken("access", "refresh", expiresAtMs = Long.MAX_VALUE))
    private val sleeps = mutableListOf<Long>()
    private val cacheDir = Files.createTempDirectory("webapi").toFile()
    private val api = SpotifyWebApi(
        transport,
        SpotifyAuth(transport, store, clientId = "client", now = { 0 }),
        ResponseCache(cacheDir),
        sleep = { sleeps += it },
    )

    @Test
    fun `204 from the player endpoint means no active player`() = runTest {
        transport.respond(204)
        assertEquals(ApiResult.NoContent, api.playbackState())
    }

    @Test
    fun `no connection is reported as offline, not as an error status`() = runTest {
        transport.failWithNetworkError()
        val result = api.pause()
        assertTrue((result as ApiResult.Failure).error is ApiError.Offline)
    }

    @Test
    fun `401 refreshes the token once and retries`() = runTest {
        transport.respond(401)
        transport.respond(200, tokenJson("new-access"))
        transport.respond(204)

        assertEquals(ApiResult.Ok(Unit), api.next())
        assertEquals("Bearer new-access", transport.requests.last().headers["Authorization"])
    }

    @Test
    fun `a second 401 is returned instead of looping`() = runTest {
        transport.respond(401)
        transport.respond(200, tokenJson("new-access"))
        transport.respond(401, """{"error":{"status":401,"message":"Invalid access token"}}""")

        val error = (api.next() as ApiResult.Failure).error as ApiError.Http
        assertEquals(401, error.status)
        assertEquals("Invalid access token", error.message)
    }

    @Test
    fun `429 waits for Retry-After and gives up after two retries`() = runTest {
        repeat(3) { transport.respond(429, headers = mapOf("Retry-After" to "2")) }

        val error = (api.pause() as ApiResult.Failure).error as ApiError.Http
        assertEquals(429, error.status)
        assertEquals(listOf(2_000L, 2_000L), sleeps)
    }

    @Test
    fun `library pages are cached until cleared`() = runTest {
        val page = """{"items":[{"id":"p1","name":"Rides","items":{"total":12}}],"next":null,"total":1}"""
        transport.respond(200, page)

        val first = api.playlists(0, 30) as ApiResult.Ok
        val second = api.playlists(0, 30) as ApiResult.Ok
        assertEquals(1, transport.requests.size)
        assertEquals(first, second)
        assertEquals(12, second.value.items.single().itemCount)

        api.clearCache(SpotifyWebApi.PLAYLISTS)
        transport.respond(200, page)
        api.playlists(0, 30)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `playlist items use the 2026 items endpoint`() = runTest {
        transport.respond(200, """{"items":[{"item":{"uri":"spotify:track:1","name":"A"}}]}""")
        val page = (api.playlistItems("abc", 0, 30) as ApiResult.Ok).value
        assertTrue(transport.requests.single().url.contains("/playlists/abc/items?"))
        assertEquals("A", page.items.single().content?.name)
    }

    @Test
    fun `liked state uses the unified library endpoint with full URIs`() = runTest {
        transport.respond(200, "[true]")
        assertEquals(ApiResult.Ok(true), api.isSaved("spotify:track:1"))
        assertTrue(transport.requests.single().url.endsWith("/me/library/contains?uris=spotify%3Atrack%3A1"))
    }

    @Test
    fun `play can target a device, and Spotify's error reason is kept`() = runTest {
        transport.respond(404, """{"error":{"status":404,"message":"Player command failed: No active device found","reason":"NO_ACTIVE_DEVICE"}}""")

        val error = (api.play(deviceId = "abc 1") as ApiResult.Failure).error as ApiError.Http
        assertTrue(transport.requests.single().url.endsWith("/me/player/play?device_id=abc+1"))
        assertEquals("NO_ACTIVE_DEVICE", error.reason)
    }
}
