package com.doughindson.karootunes.datatypes

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.LocalContext
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import com.doughindson.karootunes.spotify.PlayerError
import com.doughindson.karootunes.spotify.PlayerState

// Sample states for previewing the player widget without a live Spotify connection.
// Edit these to try out different content while iterating on layout/design.

private val sampleStatePlaying = PlayerState(
    isPlayingTrackName = "Blinding Lights",
    isPlayingArtistName = "The Weeknd",
    isPlaying = true,
    isPlayingTrackThumbnailUrls = null,
    playProgressInMs = 95_000,
    currentTrackLengthInMs = 200_000,
)

private val sampleStateWaiting = PlayerState()

private val sampleStateError = PlayerState(
    error = PlayerError("Connection lost", "Could not reach Spotify. Check your internet connection.")
)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 800)
@Composable
fun PlayerWidgetFullPagePlayingPreview() {
    val context = LocalContext.current
    PlayerWidgetFullPageRedesign(context, sampleStatePlaying, null, false, 480.dp)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 800)
@Composable
fun PlayerWidgetFullPageWaitingPreview() {
    val context = LocalContext.current
    PlayerWidgetFullPageRedesign(context, sampleStateWaiting, null, false, 480.dp)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 800)
@Composable
fun PlayerWidgetFullPageErrorPreview() {
    val context = LocalContext.current
    PlayerWidgetFullPageRedesign(context, sampleStateError, null, false, 480.dp)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 316)
@Composable
fun PlayerWidgetMidPageStateAPreview() {
    val context = LocalContext.current
    PlayerWidgetMidPageRedesign(context, sampleStatePlaying, false, 480.dp, 316.dp)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 316)
@Composable
fun PlayerWidgetMidPageStateBPreview() {
    val context = LocalContext.current
    PlayerWidgetMidPageRedesign(context, sampleStatePlaying.copy(isInOptionsMenu = true), false, 480.dp, 316.dp)
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 60)
@Composable
fun PlayerWidgetCompactPreview() {
    val context = LocalContext.current
    PlayerWidgetCompact(context, sampleStatePlaying, PlayerSize.SINGLE_FIELD, false)
}
