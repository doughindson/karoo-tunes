package com.doughindson.karootunes.playback

import com.doughindson.karootunes.data.spotify.ApiResult

/**
 * Talks to a Spotify player. [WebPlayback] controls Spotify on the phone through the Web API.
 * A second implementation (e.g. for a Spotify app sideloaded on the Karoo, see the
 * `standalone-mode-reference` git tag) can be added without touching the widget or screens.
 */
interface PlaybackController {
    /** [ApiResult.NoContent] means Spotify has no active player. */
    suspend fun snapshot(): ApiResult<PlaybackSnapshot>
    suspend fun upcoming(): ApiResult<List<Track>>
    suspend fun isLiked(uri: String): ApiResult<Boolean>

    suspend fun play(): ApiResult<Unit>
    suspend fun pause(): ApiResult<Unit>
    suspend fun next(): ApiResult<Unit>
    suspend fun previous(): ApiResult<Unit>
    suspend fun seekTo(positionMs: Int): ApiResult<Unit>
    suspend fun setShuffle(enabled: Boolean): ApiResult<Unit>
    suspend fun setRepeat(mode: RepeatMode): ApiResult<Unit>
    suspend fun setLiked(uris: List<String>, liked: Boolean): ApiResult<Unit>

    /** Plays a playlist/show/album, optionally starting at one of its items. */
    suspend fun playContext(contextUri: String, startAtUri: String? = null): ApiResult<Unit>
    suspend fun playUris(uris: List<String>): ApiResult<Unit>
    suspend fun addToQueue(uri: String): ApiResult<Unit>
}
