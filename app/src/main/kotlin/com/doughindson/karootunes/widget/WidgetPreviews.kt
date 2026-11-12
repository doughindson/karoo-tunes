package com.doughindson.karootunes.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import com.doughindson.karootunes.playback.Connection
import com.doughindson.karootunes.playback.MediaType
import com.doughindson.karootunes.playback.PlayerError
import com.doughindson.karootunes.playback.PlayerState
import com.doughindson.karootunes.playback.Track
import com.doughindson.karootunes.playback.WidgetUiState

// Sample states for iterating on the widget design in Android Studio without a Spotify connection.

private val playing = PlayerState(
    connection = Connection.ACTIVE,
    track = Track("spotify:track:1", "Blinding Lights", "The Weeknd", 200_000, MediaType.TRACK),
    isPlaying = true,
    positionMs = 95_000,
    isLiked = true,
)
private val podcast = playing.copy(track = Track("spotify:episode:1", "Episode 42: Climbing", "The Cycling Podcast", 3_600_000, MediaType.EPISODE))
private val offline = PlayerState(error = PlayerError("Offline", "Could not reach Spotify. Check the companion app connection."))

private fun model(state: PlayerState, layout: PlayerLayout, optionsOpen: Boolean = false) =
    WidgetModel.from(state, WidgetUiState(optionsOpen = optionsOpen), layout, highResArtwork = false)

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 800)
@Composable
fun FullPagePreview() = PlayerWidget(model(playing, PlayerLayout.FULL_PAGE), null, DpSize(480.dp, 800.dp))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 800)
@Composable
fun FullPagePodcastPreview() = PlayerWidget(model(podcast, PlayerLayout.FULL_PAGE), null, DpSize(480.dp, 800.dp))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 800)
@Composable
fun FullPageOfflinePreview() = PlayerWidget(model(offline, PlayerLayout.FULL_PAGE), null, DpSize(480.dp, 800.dp))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 316)
@Composable
fun MediumPreview() = PlayerWidget(model(playing, PlayerLayout.MEDIUM), null, DpSize(480.dp, 316.dp))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 316)
@Composable
fun MediumOptionsPreview() = PlayerWidget(model(playing, PlayerLayout.MEDIUM, optionsOpen = true), null, DpSize(480.dp, 316.dp))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 190)
@Composable
fun SmallPreview() = PlayerWidget(model(playing, PlayerLayout.SMALL), null, DpSize(480.dp, 190.dp))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 140)
@Composable
fun SingleFieldPreview() = PlayerWidget(model(playing, PlayerLayout.SINGLE_FIELD), null, DpSize(480.dp, 140.dp))

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 480, heightDp = 140)
@Composable
fun SingleFieldOptionsPreview() = PlayerWidget(model(playing, PlayerLayout.SINGLE_FIELD, optionsOpen = true), null, DpSize(480.dp, 140.dp))
