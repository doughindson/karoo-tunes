package com.doughindson.karootunes.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerReducerTest {
    private fun track(n: Int, duration: Int = 200_000, type: MediaType = MediaType.TRACK) =
        Track("spotify:track:$n", "Track $n", "Artist", duration, type)

    private val playing = PlayerState(
        connection = Connection.ACTIVE,
        track = track(1),
        isPlaying = true,
        positionMs = 10_000,
        positionUpdatedAt = 0,
        isLiked = true,
        previousTrack = track(0),
        upcoming = listOf(track(2), track(3)),
    )

    private fun snapshot(track: Track?, playing: Boolean = true, position: Int = 0) =
        PlaybackSnapshot(track, playing, position, shuffle = false, disallowed = emptySet())

    @Test
    fun `position advances while playing and stops at the end of the track`() {
        assertEquals(15_000, playing.positionAt(5_000))
        assertEquals(200_000, playing.positionAt(1_000_000))
        assertEquals(10_000, playing.copy(isPlaying = false).positionAt(5_000))
    }

    @Test
    fun `next shows the prefetched track immediately`() {
        val next = PlayerReducer.optimistic(playing, PlayerCommand.NEXT, now = 1_000)
        assertEquals(track(2), next.track)
        assertEquals(listOf(track(3)), next.upcoming)
        assertEquals(track(1), next.previousTrack)
        assertEquals(0, next.positionMs)
        assertNull(next.isLiked)
    }

    @Test
    fun `previous restarts the track after the first seconds`() {
        val state = playing.copy(positionMs = 60_000)
        assertTrue(PlayerReducer.previousRestartsTrack(state, now = 0))
        val result = PlayerReducer.optimistic(state, PlayerCommand.PREVIOUS, now = 0)
        assertEquals(track(1), result.track)
        assertEquals(0, result.positionMs)
    }

    @Test
    fun `previous near the start goes back to the previous track`() {
        assertFalse(PlayerReducer.previousRestartsTrack(playing, now = 0))
        val result = PlayerReducer.optimistic(playing, PlayerCommand.PREVIOUS, now = 0)
        assertEquals(track(0), result.track)
        assertEquals(listOf(track(1), track(2), track(3)), result.upcoming)
    }

    @Test
    fun `seeking is clamped to the track`() {
        assertEquals(0, PlayerReducer.optimistic(playing, PlayerCommand.SEEK_BACK, 0).positionMs)
        val nearEnd = playing.copy(positionMs = 190_000)
        assertEquals(200_000, PlayerReducer.optimistic(nearEnd, PlayerCommand.SEEK_FORWARD, 0).positionMs)
    }

    @Test
    fun `a snapshot of the same track keeps the liked state`() {
        val result = PlayerReducer.applySnapshot(playing, snapshot(track(1), position = 20_000), now = 7)
        assertEquals(true, result.isLiked)
        assertEquals(20_000, result.positionMs)
        assertEquals(7, result.positionUpdatedAt)
    }

    @Test
    fun `a snapshot of a new track resets liked and remembers the previous one`() {
        val result = PlayerReducer.applySnapshot(playing, snapshot(track(2)), now = 0)
        assertNull(result.isLiked)
        assertEquals(track(1), result.previousTrack)
        assertEquals(listOf(track(3)), result.upcoming)
    }

    @Test
    fun `a failed refresh keeps the last known playback`() {
        val result = PlayerReducer.refreshFailed(playing, PlayerError("Offline", "No connection"))
        assertEquals(track(1), result.track)
        assertTrue(result.isPlaying)
        assertEquals("Offline", result.error?.title)
    }

    @Test
    fun `commands need an active player and respect Spotify's restrictions`() {
        assertFalse(PlayerReducer.isAllowed(PlayerState(), PlayerCommand.PLAY))
        assertFalse(PlayerReducer.isAllowed(playing.copy(disallowed = setOf(PlayerCommand.NEXT)), PlayerCommand.NEXT))
        assertTrue(PlayerReducer.isAllowed(playing, PlayerCommand.NEXT))
    }

    @Test
    fun `when Spotify goes idle the last track stays and play resumes on the last device`() {
        val active = PlayerReducer.applySnapshot(playing, snapshot(track(1)).copy(deviceId = "phone", deviceName = "Pixel"), now = 0)
        val idle = PlayerReducer.noPlayer(active)

        assertEquals(Connection.NO_PLAYER, idle.connection)
        assertEquals(track(1), idle.track)
        assertFalse(idle.isPlaying)
        assertEquals("phone", idle.lastDeviceId)
        assertEquals("Press play to resume on Pixel", idle.error?.message)
        assertTrue(PlayerReducer.isAllowed(idle, PlayerCommand.PLAY))
        assertFalse(PlayerReducer.isAllowed(idle, PlayerCommand.NEXT))
    }

    @Test
    fun `idle without a known device asks to start Spotify`() {
        val idle = PlayerReducer.noPlayer(PlayerState())
        assertEquals("No active player", idle.error?.title)
        assertFalse(PlayerReducer.isAllowed(idle, PlayerCommand.PLAY))
    }

    @Test
    fun `a device that could not be woken is no longer offered for resuming`() {
        val idle = PlayerReducer.noPlayer(playing.copy(lastDeviceId = "iphone", lastDeviceName = "iPhone"))
        val failed = PlayerReducer.resumeFailed(idle)
        assertNull(failed.lastDeviceId)
        assertFalse(PlayerReducer.isAllowed(failed, PlayerCommand.PLAY))
        assertEquals("Start playing Spotify on your phone", PlayerReducer.noPlayer(failed).error?.message)
    }

    @Test
    fun `a refresh that lags behind an accepted play keeps the button as pressed`() {
        val expectation = PlayExpectation(isPlaying = true, untilMs = 10_000)

        val (stale, stillExpected) = PlayerReducer.reconcile(snapshot(track(1), playing = false), expectation, now = 4_000)
        assertTrue(stale.isPlaying)
        assertEquals(expectation, stillExpected)

        val (caughtUp, done) = PlayerReducer.reconcile(snapshot(track(1), playing = true), expectation, now = 6_000)
        assertTrue(caughtUp.isPlaying)
        assertNull(done)

        // After the window, Spotify's answer wins (e.g. playback really did stop).
        val (expired, gone) = PlayerReducer.reconcile(snapshot(track(1), playing = false), expectation, now = 11_000)
        assertFalse(expired.isPlaying)
        assertNull(gone)
    }
}
