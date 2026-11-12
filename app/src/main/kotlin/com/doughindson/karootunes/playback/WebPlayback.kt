package com.doughindson.karootunes.playback

import com.doughindson.karootunes.data.spotify.ApiResult
import com.doughindson.karootunes.data.spotify.SpotifyWebApi
import com.doughindson.karootunes.data.spotify.map
import com.doughindson.karootunes.data.spotify.model.Item
import com.doughindson.karootunes.data.spotify.model.Offset
import com.doughindson.karootunes.data.spotify.model.PlayRequest
import com.doughindson.karootunes.data.spotify.model.PlaybackStateResponse
import com.doughindson.karootunes.data.spotify.model.mediumUrl
import com.doughindson.karootunes.data.spotify.model.smallestUrl

class WebPlayback(private val api: SpotifyWebApi) : PlaybackController {

    override suspend fun snapshot(): ApiResult<PlaybackSnapshot> = api.playbackState().map { it.toSnapshot() }


    override suspend fun isLiked(uri: String) = api.isSaved(uri)

    override suspend fun play(deviceId: String?) = api.play(deviceId = deviceId)
    override suspend fun pause() = api.pause()
    override suspend fun next() = api.next()
    override suspend fun previous() = api.previous()
    override suspend fun seekTo(positionMs: Int) = api.seek(positionMs)
    override suspend fun setShuffle(enabled: Boolean) = api.setShuffle(enabled)
    override suspend fun setLiked(uris: List<String>, liked: Boolean) = api.setSaved(uris, liked)

    override suspend fun playContext(contextUri: String, startAtUri: String?) =
        api.play(PlayRequest(contextUri = contextUri, offset = startAtUri?.let { Offset(uri = it) }))

    override suspend fun playUris(uris: List<String>) = api.play(PlayRequest(uris = uris))
    override suspend fun addToQueue(uri: String) = api.addToQueue(uri)
}

internal fun PlaybackStateResponse.toSnapshot(): PlaybackSnapshot {
    val disallows = actions?.disallows
    val disallowed = buildSet {
        if (disallows?.pausing == true) add(PlayerCommand.PAUSE)
        if (disallows?.resuming == true) add(PlayerCommand.PLAY)
        if (disallows?.skippingNext == true) add(PlayerCommand.NEXT)
        if (disallows?.skippingPrev == true) add(PlayerCommand.PREVIOUS)
        if (disallows?.seeking == true) {
            add(PlayerCommand.SEEK_BACK)
            add(PlayerCommand.SEEK_FORWARD)
        }
        if (disallows?.togglingShuffle == true) add(PlayerCommand.TOGGLE_SHUFFLE)
    }
    return PlaybackSnapshot(
        track = item?.toTrack(currentlyPlayingType),
        isPlaying = isPlaying == true,
        positionMs = progressMs ?: 0,
        shuffle = shuffleState == true,
        disallowed = disallowed,
        deviceId = device?.id,
        deviceName = device?.name,
    )
}

internal fun Item.toTrack(playingType: String? = null): Track {
    val type = when (playingType ?: type) {
        "track" -> MediaType.TRACK
        "episode" -> MediaType.EPISODE
        "ad" -> MediaType.AD
        else -> MediaType.UNKNOWN
    }
    val images = allImages ?: show?.images
    return Track(
        uri = uri,
        name = name,
        subtitle = if (isEpisode) show?.name else artistNames,
        durationMs = durationMs,
        type = type,
        smallImageUrl = images.smallestUrl(),
        mediumImageUrl = images.mediumUrl(),
    )
}
