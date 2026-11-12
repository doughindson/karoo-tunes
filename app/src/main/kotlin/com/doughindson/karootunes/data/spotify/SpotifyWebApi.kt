package com.doughindson.karootunes.data.spotify

import android.util.Log
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.AppJson
import com.doughindson.karootunes.data.auth.AuthServiceException
import com.doughindson.karootunes.data.auth.NotLoggedInException
import com.doughindson.karootunes.data.auth.SpotifyAuth
import com.doughindson.karootunes.data.http.HttpResponse
import com.doughindson.karootunes.data.http.HttpTransport
import com.doughindson.karootunes.data.http.NetworkException
import com.doughindson.karootunes.data.spotify.model.Item
import com.doughindson.karootunes.data.spotify.model.Paging
import com.doughindson.karootunes.data.spotify.model.PlayRequest
import com.doughindson.karootunes.data.spotify.model.PlaybackStateResponse
import com.doughindson.karootunes.data.spotify.model.Playlist
import com.doughindson.karootunes.data.spotify.model.PlaylistItem
import com.doughindson.karootunes.data.spotify.model.QueueResponse
import com.doughindson.karootunes.data.spotify.model.SavedEpisode
import com.doughindson.karootunes.data.spotify.model.SavedShow
import com.doughindson.karootunes.data.spotify.model.SavedTrack
import com.doughindson.karootunes.data.spotify.model.WebApiErrorResponse
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

/** Spotify Web API. Every call returns an [ApiResult]; nothing here throws for network or HTTP errors. */
class SpotifyWebApi(
    private val transport: HttpTransport,
    private val auth: SpotifyAuth,
    private val cache: ResponseCache,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    // --- Player -----------------------------------------------------------------------------

    suspend fun playbackState(): ApiResult<PlaybackStateResponse> =
        get("/me/player?additional_types=episode", PlaybackStateResponse.serializer())

    suspend fun queue(): ApiResult<QueueResponse> = get("/me/player/queue", QueueResponse.serializer())

    /** With [deviceId], playback (re)starts on that device even if Spotify no longer considers it active. */
    suspend fun play(request: PlayRequest? = null, deviceId: String? = null): ApiResult<Unit> =
        command(
            "PUT",
            "/me/player/play" + (deviceId?.let { "?device_id=${encode(it)}" } ?: ""),
            request?.let { AppJson.encodeToString(PlayRequest.serializer(), it) },
        )

    suspend fun pause() = command("PUT", "/me/player/pause")
    suspend fun next() = command("POST", "/me/player/next")
    suspend fun previous() = command("POST", "/me/player/previous")
    suspend fun seek(positionMs: Int) = command("PUT", "/me/player/seek?position_ms=${positionMs.coerceAtLeast(0)}")
    suspend fun setShuffle(enabled: Boolean) = command("PUT", "/me/player/shuffle?state=$enabled")
    suspend fun addToQueue(uri: String) = command("POST", "/me/player/queue?uri=${encode(uri)}")

    // --- Library (the 2026 unified endpoints take full spotify: URIs) -------------------------

    suspend fun isSaved(uri: String): ApiResult<Boolean> =
        get("/me/library/contains?uris=${encode(uri)}", ListSerializer(Boolean.serializer())).map { it.firstOrNull() == true }

    suspend fun setSaved(uris: List<String>, saved: Boolean): ApiResult<Unit> =
        command(if (saved) "PUT" else "DELETE", "/me/library?uris=${encode(uris.joinToString(","))}")

    // --- Browsing (cached) -------------------------------------------------------------------

    suspend fun playlists(offset: Int, limit: Int) =
        cachedGet(PLAYLISTS, "/me/playlists?offset=$offset&limit=$limit", Paging.serializer(Playlist.serializer()))

    /** Only works for playlists the user owns or collaborates on (Spotify Development Mode restriction). */
    suspend fun playlistItems(playlistId: String, offset: Int, limit: Int) =
        cachedGet(playlistPrefix(playlistId), "/playlists/$playlistId/items?offset=$offset&limit=$limit&additional_types=track,episode", Paging.serializer(PlaylistItem.serializer()))

    suspend fun savedTracks(offset: Int, limit: Int) =
        cachedGet(SAVED_TRACKS, "/me/tracks?offset=$offset&limit=$limit", Paging.serializer(SavedTrack.serializer()))

    suspend fun savedShows(offset: Int, limit: Int) =
        cachedGet(SHOWS, "/me/shows?offset=$offset&limit=$limit", Paging.serializer(SavedShow.serializer()))

    suspend fun showEpisodes(showId: String, offset: Int, limit: Int) =
        cachedGet(showPrefix(showId), "/shows/$showId/episodes?offset=$offset&limit=$limit", Paging.serializer(Item.serializer()))

    suspend fun savedEpisodes(offset: Int, limit: Int) =
        cachedGet(SAVED_EPISODES, "/me/episodes?offset=$offset&limit=$limit", Paging.serializer(SavedEpisode.serializer()))

    fun clearCache(prefix: String) = cache.clear(prefix)

    // --- Plumbing ----------------------------------------------------------------------------

    private suspend fun <T> get(path: String, serializer: KSerializer<T>): ApiResult<T> =
        when (val result = send("GET", path, null)) {
            is ApiResult.Ok -> decode(result.value, serializer)
            ApiResult.NoContent -> ApiResult.NoContent
            is ApiResult.Failure -> result
        }

    private suspend fun <T> cachedGet(prefix: String, path: String, serializer: KSerializer<T>): ApiResult<T> {
        cache.read(prefix, path)?.let { cached ->
            runCatching { return ApiResult.Ok(AppJson.decodeFromString(serializer, cached)) }
        }
        val result = send("GET", path, null)
        if (result is ApiResult.Ok) {
            val decoded = decode(result.value, serializer)
            if (decoded is ApiResult.Ok) cache.write(prefix, path, result.value.bodyText())
            return decoded
        }
        return (result as? ApiResult.Failure) ?: ApiResult.NoContent
    }

    private suspend fun command(method: String, path: String, jsonBody: String? = null): ApiResult<Unit> =
        when (val result = send(method, path, jsonBody)) {
            is ApiResult.Ok, ApiResult.NoContent -> ApiResult.Ok(Unit)
            is ApiResult.Failure -> result
        }

    private fun <T> decode(response: HttpResponse, serializer: KSerializer<T>): ApiResult<T> {
        if (response.body.isEmpty()) return ApiResult.NoContent
        return try {
            ApiResult.Ok(AppJson.decodeFromString(serializer, response.bodyText()))
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected Spotify response", e)
            ApiResult.Failure(ApiError.Unexpected("Unexpected response from Spotify"))
        }
    }

    /** One request with auth, a single 401 refresh-and-retry and bounded 429 back-off. */
    private suspend fun send(method: String, path: String, jsonBody: String?): ApiResult<HttpResponse> {
        var refreshed = false
        var rateLimitRetries = 0
        var token = authCall { auth.accessToken() }.let { if (it is ApiResult.Ok) it.value else return it as ApiResult.Failure }

        while (true) {
            val headers = buildMap {
                put("Authorization", "Bearer $token")
                put("Accept-Encoding", "gzip")
                if (jsonBody != null) put("Content-Type", "application/json")
            }
            val started = System.nanoTime()
            val response = try {
                transport.request(method, BASE_URL + path, headers, jsonBody?.encodeToByteArray())
            } catch (e: NetworkException) {
                Log.d(TAG, "$method $path failed after ${elapsedMs(started)} ms: ${e.message}")
                return ApiResult.Failure(ApiError.Offline(e.message ?: "No connection"))
            }
            Log.d(TAG, "$method $path -> ${response.status} in ${elapsedMs(started)} ms")

            when {
                response.status == 204 -> return ApiResult.NoContent
                response.isSuccess -> return ApiResult.Ok(response)
                response.status == 401 && !refreshed -> {
                    refreshed = true
                    val rejected = token
                    token = authCall { auth.refreshAfterRejection(rejected) }.let { if (it is ApiResult.Ok) it.value else return it as ApiResult.Failure }
                }
                response.status == 429 && rateLimitRetries < MAX_RATE_LIMIT_RETRIES -> {
                    rateLimitRetries++
                    val waitSeconds = (response.header("Retry-After")?.toLongOrNull() ?: 5L).coerceIn(1L, MAX_RETRY_AFTER_SECONDS)
                    Log.w(TAG, "Rate limited by Spotify, retrying in ${waitSeconds}s")
                    sleep(waitSeconds * 1000)
                }
                else -> {
                    val error = response.webApiError()
                    return ApiResult.Failure(
                        ApiError.Http(response.status, error?.message?.takeIf { it.isNotBlank() } ?: "HTTP ${response.status}", error?.reason)
                    )
                }
            }
        }
    }

    private suspend fun authCall(block: suspend () -> String): ApiResult<String> = try {
        ApiResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: NotLoggedInException) {
        ApiResult.Failure(ApiError.NotLoggedIn(e.message ?: "Not logged in"))
    } catch (e: NetworkException) {
        ApiResult.Failure(ApiError.Offline(e.message ?: "No connection"))
    } catch (e: AuthServiceException) {
        ApiResult.Failure(ApiError.Http(e.status, e.message ?: "Spotify login error"))
    }

    private fun HttpResponse.webApiError() =
        runCatching { AppJson.decodeFromString(WebApiErrorResponse.serializer(), bodyText()).error }.getOrNull()

    private fun elapsedMs(startNanos: Long) = (System.nanoTime() - startNanos) / 1_000_000

    companion object {
        const val BASE_URL = "https://api.spotify.com/v1"
        const val PAGE_SIZE = 30
        private const val MAX_RATE_LIMIT_RETRIES = 2
        private const val MAX_RETRY_AFTER_SECONDS = 30L

        const val PLAYLISTS = "playlists"
        const val SAVED_TRACKS = "saved_tracks"
        const val SHOWS = "shows"
        const val SAVED_EPISODES = "saved_episodes"
        fun playlistPrefix(id: String) = "playlist_$id"
        fun showPrefix(id: String) = "show_$id"

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    }
}
