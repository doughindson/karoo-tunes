package com.doughindson.karootunes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.doughindson.karootunes.R
import com.doughindson.karootunes.ui.components.BackButton
import com.doughindson.karootunes.ui.components.FilterChips
import com.doughindson.karootunes.ui.components.IconTile
import com.doughindson.karootunes.ui.components.InfoMessage
import com.doughindson.karootunes.ui.components.ListRow
import com.doughindson.karootunes.ui.components.PillButton
import com.doughindson.karootunes.ui.components.PlayButton
import com.doughindson.karootunes.ui.components.ScreenHeader
import com.doughindson.karootunes.ui.components.SpotifyIcon
import com.doughindson.karootunes.ui.theme.AppTheme
import com.doughindson.karootunes.ui.theme.LikedSongsGradient

// Design previews at the Karoo 3's screen size (256 x 427 dp), in both themes. The real screens load
// data through Koin, so these compose the same building blocks with sample content.

@Composable
private fun KarooFrame(dark: Boolean, content: @Composable BoxScope.() -> Unit) {
    AppTheme(darkTheme = dark) {
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize()) { content() }
        }
    }
}

@Composable
private fun SampleArt() = IconTile(R.drawable.ic_music_note, Brush.linearGradient(listOf(Color(0xFFE8A33D), Color(0xFFB2412F))), Color.White)

@Composable
private fun LibrarySample(dark: Boolean) = KarooFrame(dark) {
    Column {
        ScreenHeader("Spotify")
        FilterChips(listOf("Playlists", "Podcasts"), selected = 0, onSelect = {})
        ListRow("Liked Songs", "Your saved songs", onClick = {}) {
            IconTile(R.drawable.ic_favorite, Brush.linearGradient(LikedSongsGradient), Color.White)
        }
        ListRow("Habibi Funk - Essentials", "Playlist • 71 songs", onClick = {}) { SampleArt() }
        ListRow("Summer Sun", "Playlist • 24 songs", onClick = {}) { SampleArt() }
    }
    BackButton {}
}

@Composable
private fun TrackListSample(dark: Boolean) = KarooFrame(dark) {
    Column {
        ScreenHeader("Habibi Funk - Essentials", maxLines = 2) {
            Spacer(Modifier.width(8.dp))
            SpotifyIcon(22.dp)
            Spacer(Modifier.width(12.dp))
            PlayButton(onClick = {})
        }
        InfoMessage("Spotify only lists the tracks of playlists you own or collaborate on. You can still play the whole playlist.")
        ListRow("where would i be without you", "Jafro, NIKUL", onClick = {}) { SampleArt() }
        ListRow("Episode 42: Climbing the Galibier", "Mar 1 • 45 min", onClick = {}, titleLines = 2) { SampleArt() }
    }
    BackButton {}
}

@Composable
private fun HomeSample(dark: Boolean) = KarooFrame(dark) {
    Column {
        ScreenHeader("Karoo Tunes")
        Column(Modifier.padding(horizontal = 16.dp)) {
            PillButton("Log in with Spotify", onClick = {}, primary = true, icon = R.drawable.spotify_icon_black)
            Spacer(Modifier.height(12.dp))
            PillButton("Preview player", onClick = {}, icon = R.drawable.ic_play_arrow)
        }
    }
}

@Preview(widthDp = 256, heightDp = 427, name = "Library, dark") @Composable private fun LibraryDark() = LibrarySample(dark = true)
@Preview(widthDp = 256, heightDp = 427, name = "Library, light") @Composable private fun LibraryLight() = LibrarySample(dark = false)
@Preview(widthDp = 256, heightDp = 427, name = "Track list, dark") @Composable private fun TrackListDark() = TrackListSample(dark = true)
@Preview(widthDp = 256, heightDp = 427, name = "Track list, light") @Composable private fun TrackListLight() = TrackListSample(dark = false)
@Preview(widthDp = 256, heightDp = 427, name = "Home, dark") @Composable private fun HomeDark() = HomeSample(dark = true)
@Preview(widthDp = 256, heightDp = 427, name = "Home, light") @Composable private fun HomeLight() = HomeSample(dark = false)
