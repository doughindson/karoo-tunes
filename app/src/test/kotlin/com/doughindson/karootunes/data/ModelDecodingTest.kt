package com.doughindson.karootunes.data

import com.doughindson.karootunes.data.http.gunzipIfNeeded
import com.doughindson.karootunes.data.spotify.model.Paging
import com.doughindson.karootunes.data.spotify.model.PlaybackStateResponse
import com.doughindson.karootunes.data.spotify.model.Playlist
import com.doughindson.karootunes.data.spotify.model.PlaylistItem
import com.doughindson.karootunes.data.spotify.model.SavedTrack
import com.doughindson.karootunes.playback.MediaType
import com.doughindson.karootunes.playback.PlayerCommand
import com.doughindson.karootunes.playback.RepeatMode
import com.doughindson.karootunes.playback.toSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class ModelDecodingTest {
    @Test
    fun `playlist item counts are read from both the old and the 2026 field names`() {
        assertEquals(5, AppJson.decodeFromString(Playlist.serializer(), """{"items":{"total":5}}""").itemCount)
        assertEquals(7, AppJson.decodeFromString(Playlist.serializer(), """{"tracks":{"total":7}}""").itemCount)
    }

    @Test
    fun `playlist items accept both item and track`() {
        val page = AppJson.decodeFromString(
            Paging.serializer(PlaylistItem.serializer()),
            """{"items":[{"item":{"name":"New"}},{"track":{"name":"Old"}},{"track":null}],"next":null}""",
        )
        assertEquals(listOf("New", "Old", null), page.items.map { it.content?.name })
    }

    @Test
    fun `saved tracks read added_at and tolerate removed fields`() {
        val page = AppJson.decodeFromString(
            Paging.serializer(SavedTrack.serializer()),
            """{"items":[{"added_at":"2026-01-01T00:00:00Z","track":{"name":"A","external_ids":{},"popularity":null}}]}""",
        )
        assertEquals("2026-01-01T00:00:00Z", page.items.single().addedAt)
    }

    @Test
    fun `episode playback state maps to a podcast snapshot`() {
        val json = """
            {"is_playing":true,"progress_ms":1000,"shuffle_state":false,"repeat_state":"track",
             "currently_playing_type":"episode",
             "item":{"type":"episode","uri":"spotify:episode:1","name":"Ep 1","duration_ms":60000,
                     "images":[{"url":"big"},{"url":"medium"},{"url":"small"}],"show":{"name":"The Show"}},
             "actions":{"disallows":{"skipping_prev":true,"seeking":true}}}
        """.trimIndent()
        val snapshot = AppJson.decodeFromString(PlaybackStateResponse.serializer(), json).toSnapshot()

        assertEquals(MediaType.EPISODE, snapshot.track?.type)
        assertEquals("The Show", snapshot.track?.subtitle)
        assertEquals("small", snapshot.track?.smallImageUrl)
        assertEquals("medium", snapshot.track?.mediumImageUrl)
        assertEquals(RepeatMode.TRACK, snapshot.repeat)
        assertEquals(setOf(PlayerCommand.PREVIOUS, PlayerCommand.SEEK_BACK, PlayerCommand.SEEK_FORWARD), snapshot.disallowed)
        assertTrue(snapshot.isPlaying)
    }

    @Test
    fun `gzip bodies are decompressed and plain bodies passed through`() {
        val plain = """{"a":1}""".encodeToByteArray()
        val gzipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(plain) } }.toByteArray()
        assertArrayEquals(plain, gzipped.gunzipIfNeeded())
        assertArrayEquals(plain, plain.gunzipIfNeeded())
    }
}
