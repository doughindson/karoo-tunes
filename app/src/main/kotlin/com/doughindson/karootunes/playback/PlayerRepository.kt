package com.doughindson.karootunes.playback

import android.os.SystemClock
import android.util.Log
import com.doughindson.karootunes.AppScope
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.ThumbnailCache
import com.doughindson.karootunes.data.auth.SpotifyAuth
import com.doughindson.karootunes.data.karoo.KarooGateway
import com.doughindson.karootunes.data.settings.SettingsRepository
import com.doughindson.karootunes.data.spotify.ApiError
import com.doughindson.karootunes.data.spotify.ApiResult
import com.doughindson.karootunes.widget.PlayerDataType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the player state and keeps it in sync with Spotify while the player is (or may soon be) on
 * screen. Start/stop with the extension service; [start] after [stop] works.
 */
class PlayerRepository(
    private val controller: PlaybackController,
    private val auth: SpotifyAuth,
    private val karoo: KarooGateway,
    private val settings: SettingsRepository,
    private val visibility: PlayerVisibility,
    private val thumbnails: ThumbnailCache,
    private val appScope: AppScope,
    val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val commandsInFlight = MutableStateFlow(0)

    @Volatile
    private var playExpectation: PlayExpectation? = null
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = appScope.launch {
            launch { pollWhileVisible() }
            launch { lookUpLikedState() }
            launch {
                auth.isLoggedIn.collect { loggedIn ->
                    if (!loggedIn) _state.value = PlayerReducer.notLoggedIn()
                    requestRefresh()
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    fun updateAndGet(transform: (PlayerState) -> PlayerState): PlayerState = _state.updateAndGet(transform)

    /** While a command is in flight, poll results may predate it and would undo the optimistic update. */
    /** A play/pause was accepted: don't let a lagging refresh flip the button back for a few seconds. */
    fun expectPlaying(isPlaying: Boolean) {
        playExpectation = PlayExpectation(isPlaying, now() + STALE_READ_WINDOW_MS)
    }

    fun beginCommand() = commandsInFlight.update { it + 1 }
    fun endCommand() = commandsInFlight.update { it - 1 }

    /** The Karoo sends one request at a time: background requests wait until commands are through. */
    private suspend fun awaitNoCommands() {
        commandsInFlight.first { it == 0 }
    }

    /** How often to refresh: quickly while the player is on screen, slowly while it's only in the profile. */
    private enum class PollMode(val intervalMs: Long) {
        OFF(0),
        ON_SCREEN(10_000L),
        IN_PROFILE(45_000L),
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun pollMode(): Flow<PollMode> = combine(
        auth.isLoggedIn,
        karoo.connected,
        visibility.previews,
        settings.settings.map { it.onlyRefreshOnActivePage }.distinctUntilChanged(),
    ) { loggedIn, karooConnected, previews, onlyOnActivePage ->
        PollInputs(loggedIn, karooConnected, previews > 0, onlyOnActivePage)
    }.distinctUntilChanged().flatMapLatest { inputs ->
        when {
            !inputs.loggedIn -> flowOf(PollMode.OFF)
            // Not on a Karoo (emulator / phone): nothing to tell us whether the field is visible.
            !inputs.karooConnected || inputs.previewOpen -> flowOf(PollMode.ON_SCREEN)
            inputs.onlyOnActivePage -> karoo.isDataTypeOnScreen(PlayerDataType.DATA_TYPE_ID)
                .map { onScreen -> if (onScreen) PollMode.ON_SCREEN else PollMode.OFF }
            else -> combine(
                karoo.isDataTypeOnScreen(PlayerDataType.DATA_TYPE_ID),
                karoo.isDataTypeInActiveProfile(PlayerDataType.DATA_TYPE_ID),
            ) { onScreen, inProfile ->
                when {
                    onScreen -> PollMode.ON_SCREEN
                    inProfile -> PollMode.IN_PROFILE
                    else -> PollMode.OFF
                }
            }
        }
    }.distinctUntilChanged()

    private data class PollInputs(val loggedIn: Boolean, val karooConnected: Boolean, val previewOpen: Boolean, val onlyOnActivePage: Boolean)

    private suspend fun pollWhileVisible() {
        pollMode().collectLatest { mode ->
            Log.d(TAG, "Player polling: $mode")
            if (mode == PollMode.OFF) return@collectLatest
            while (true) {
                awaitNoCommands()
                poll()
                val requested = withTimeoutOrNull(nextPollDelayMs(mode)) { refreshRequests.receive() } != null
                // After a command, wait until presses stop before refreshing. The Karoo sends one request
                // at a time, so a refresh in between would hold up the next press by seconds.
                if (requested) {
                    while (withTimeoutOrNull(COMMAND_SETTLE_MS) { refreshRequests.receive() } != null) { /* another command: keep waiting */ }
                }
            }
        }
    }

    private fun nextPollDelayMs(mode: PollMode): Long {
        val current = _state.value
        val interval = mode.intervalMs
        // The rider is probably about to start Spotify on the phone; notice it quickly.
        if (current.connection == Connection.NO_PLAYER) return minOf(interval, NO_PLAYER_INTERVAL_MS)
        val duration = current.track?.durationMs
        if (!current.isPlaying || duration == null) return interval
        // Refresh just after the current track ends so the next one shows up promptly.
        val untilEnd = (duration - current.positionAt(now())).toLong() + TRACK_END_GRACE_MS
        return untilEnd.coerceIn(MIN_POLL_INTERVAL_MS, interval)
    }

    private suspend fun poll() {
        val result = controller.snapshot()
        Log.d(TAG, "Poll: ${result.describe()}")
        if (commandsInFlight.value > 0) return
        var staleRead = false
        _state.update { current ->
            when (result) {
                is ApiResult.Ok -> {
                    val (snapshot, stillExpected) = PlayerReducer.reconcile(result.value, playExpectation, now())
                    playExpectation = stillExpected
                    staleRead = stillExpected != null
                    PlayerReducer.applySnapshot(current, snapshot, now())
                }
                ApiResult.NoContent -> {
                    playExpectation = null
                    PlayerReducer.noPlayer(current)
                }
                is ApiResult.Failure -> when (val error = result.error) {
                    is ApiError.NotLoggedIn -> PlayerReducer.notLoggedIn()
                    is ApiError.Offline -> PlayerReducer.refreshFailed(current, PlayerError("Offline", error.message))
                    else -> PlayerReducer.refreshFailed(current, PlayerError("Spotify error", error.message))
                }
            }
        }
        if (staleRead) {
            Log.d(TAG, "Spotify hasn't caught up with play/pause yet; checking again")
            requestRefresh()
        }
        _state.value.track?.let { track ->
            val highRes = settings.current().highResThumbnails
            track.imageUrl(highRes)?.let(thumbnails::prefetch)
        }
    }

    private suspend fun lookUpLikedState() {
        _state.map { it.track?.uri to it.isLiked }
            .distinctUntilChanged()
            .filter { (uri, liked) -> uri != null && liked == null }
            .collectLatest { (uri, _) ->
                // Wait until the track has settled (Next/Previous show it before Spotify confirms) and
                // any command is through, so this lookup never sits ahead of a button press.
                delay(LIKED_LOOKUP_DELAY_MS)
                awaitNoCommands()
                val liked = (controller.isLiked(uri!!) as? ApiResult.Ok)?.value ?: return@collectLatest
                _state.update { if (it.track?.uri == uri && it.isLiked == null) it.copy(isLiked = liked) else it }
            }
    }


    private fun ApiResult<PlaybackSnapshot>.describe(): String = when (this) {
        is ApiResult.Ok -> "${if (value.isPlaying) "playing" else "paused"} ${value.track?.name} on ${value.deviceName}"
        ApiResult.NoContent -> "no active device"
        is ApiResult.Failure -> "failed: $error"
    }

    private companion object {
        const val MIN_POLL_INTERVAL_MS = 3_000L
        const val TRACK_END_GRACE_MS = 2_000L
        const val COMMAND_SETTLE_MS = 2_500L
        const val NO_PLAYER_INTERVAL_MS = 10_000L
        const val LIKED_LOOKUP_DELAY_MS = 3_000L
        const val STALE_READ_WINDOW_MS = 10_000L
    }
}
