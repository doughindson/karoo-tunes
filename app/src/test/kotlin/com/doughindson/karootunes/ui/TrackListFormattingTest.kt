package com.doughindson.karootunes.ui

import com.doughindson.karootunes.ui.tracks.episodeSubtitle
import com.doughindson.karootunes.ui.tracks.likedSongsContextUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TrackListFormattingTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun `liked songs play as the user's collection`() {
        assertEquals("spotify:user:doug123:collection", likedSongsContextUri("doug123"))
    }

    @Test
    fun `episode subtitles show date and length like Spotify`() {
        assertEquals("Mar 1 • 45 min", episodeSubtitle("2026-03-01", 45 * 60_000, today, Locale.US))
        assertEquals("Mar 1, 2025 • 1 hr 5 min", episodeSubtitle("2025-03-01", 65 * 60_000, today, Locale.US))
        assertEquals("1 min", episodeSubtitle(null, 20_000, today, Locale.US))
        // Spotify sometimes gives only a year; skip what can't be parsed.
        assertEquals("30 min", episodeSubtitle("2024", 30 * 60_000, today, Locale.US))
        assertNull(episodeSubtitle(null, null, today, Locale.US))
    }
}
