package com.doughindson.karootunes.widget

import com.doughindson.karootunes.R
import com.doughindson.karootunes.playback.Connection
import com.doughindson.karootunes.playback.MediaType
import com.doughindson.karootunes.playback.PlayerCommand
import com.doughindson.karootunes.playback.PlayerError
import com.doughindson.karootunes.playback.PlayerState
import com.doughindson.karootunes.playback.Track
import com.doughindson.karootunes.playback.WidgetUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetModelTest {
    private val track = Track("spotify:track:1", "Song", "Band", 100_000, MediaType.TRACK, smallImageUrl = "s", mediumImageUrl = "m")
    private val playing = PlayerState(connection = Connection.ACTIVE, track = track, isPlaying = true, positionMs = 50_000)

    private fun model(state: PlayerState, layout: PlayerLayout = PlayerLayout.FULL_PAGE, ui: WidgetUiState = WidgetUiState(), highRes: Boolean = false) =
        WidgetModel.from(state, ui, layout, highRes, now = 0)

    @Test
    fun `layout follows the field's grid size and real height`() {
        assertEquals(PlayerLayout.SINGLE_FIELD, PlayerLayout.forField(30 to 60, 800f))
        assertEquals(PlayerLayout.SMALL, PlayerLayout.forField(60 to 20, 200f))
        assertEquals(PlayerLayout.SINGLE_FIELD, PlayerLayout.forField(60 to 20, 120f))
        assertEquals(PlayerLayout.MEDIUM, PlayerLayout.forField(60 to 30 + 1, 300f))
        assertEquals(PlayerLayout.FULL_PAGE, PlayerLayout.forField(60 to 60, 800f))
    }

    @Test
    fun `track text and progress`() {
        val m = model(playing)
        assertEquals(WidgetText.Literal("Song"), m.title)
        assertEquals(WidgetText.Literal("Band"), m.subtitle)
        assertEquals(0.5f, m.progress)
        assertEquals("s", m.artworkUrl)
        assertEquals("m", model(playing, highRes = true).artworkUrl)
    }

    @Test
    fun `layouts without a progress bar or artwork don't depend on time or images`() {
        val m = model(playing, PlayerLayout.SMALL)
        assertNull(m.progress)
        assertNull(m.artworkUrl)
        // Same model a second later: no re-render needed.
        assertEquals(m, WidgetModel.from(playing, WidgetUiState(), PlayerLayout.SMALL, false, now = 1_000))
    }

    @Test
    fun `skipping without a prefetched track shows a loading title`() {
        val state = playing.copy(track = null)
        val m = model(state, ui = WidgetUiState(pending = setOf(PlayerCommand.NEXT)))
        assertEquals(WidgetText.Resource(R.string.widget_loading_next), m.title)
        assertTrue(m.titleMuted)
    }

    @Test
    fun `errors without a track are shown in full`() {
        val m = model(PlayerState(error = PlayerError("Offline", "No connection")))
        assertEquals(WidgetText.Literal("Offline"), m.title)
        assertEquals(3, m.subtitleMaxLines)
        assertFalse(m.isEnabled(PlayerCommand.PLAY))
    }

    @Test
    fun `pending commands are disabled`() {
        val m = model(playing, ui = WidgetUiState(pending = setOf(PlayerCommand.PAUSE)))
        assertFalse(m.isEnabled(PlayerCommand.PAUSE))
        assertTrue(m.isEnabled(PlayerCommand.NEXT))
    }

    @Test
    fun `podcasts get seek buttons instead of skip`() {
        val episode = playing.copy(track = track.copy(type = MediaType.EPISODE))
        assertEquals(
            listOf(PlayerCommand.SEEK_BACK, PlayerCommand.PAUSE, PlayerCommand.SEEK_FORWARD),
            transportCommands(model(episode)),
        )
        assertEquals(
            listOf(PlayerCommand.PREVIOUS, PlayerCommand.PAUSE, PlayerCommand.NEXT),
            transportCommands(model(playing)),
        )
    }
}
