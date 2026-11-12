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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

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
    private val commandsInFlight = AtomicInteger(0)
    private var job: Job? = null

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = appScope.launch {
            launch { pollWhileVisible() }
            launch { lookUpLikedState() }
            launch { prefetchUpcoming() }
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
    fun beginCommand() = commandsInFlight.incrementAndGet()
    fun endCommand() = commandsInFlight.decrementAndGet()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun shouldPoll(): Flow<Boolean> = combine(
        auth.isLoggedIn,
        karoo.connected,
        visibility.previews,
        settings.settings.map { it.onlyRefreshOnActivePage }.distinctUntilChanged(),
    ) { loggedIn, karooConnected, previews, onlyOnActivePage ->
        PollInputs(loggedIn, karooConnected, previews > 0, onlyOnActivePage)
    }.distinctUntilChanged().flatMapLatest { inputs ->
        when {
            !inputs.loggedIn -> flowOf(false)
            // Not on a Karoo (emulator / phone): nothing to tell us whether the field is visible.
            !inputs.karooConnected || inputs.previewOpen -> flowOf(true)
            inputs.onlyOnActivePage -> karoo.isDataTypeOnScreen(PlayerDataType.DATA_TYPE_ID)
            else -> karoo.isDataTypeInActiveProfile(PlayerDataType.DATA_TYPE_ID)
        }
    }.distinctUntilChanged()

    private data class PollInputs(val loggedIn: Boolean, val karooConnected: Boolean, val previewOpen: Boolean, val onlyOnActivePage: Boolean)

    private suspend fun pollWhileVisible() {
        shouldPoll().collectLatest { active ->
            Log.d(TAG, "Player polling active: $active")
            if (!active) return@collectLatest
            while (true) {
                poll()
                val requested = withTimeoutOrNull(nextPollDelayMs()) { refreshRequests.receive() } != null
                // Give Spotify a moment to apply a command before asking for the new state.
                if (requested) delay(COMMAND_SETTLE_MS)
            }
        }
    }

    private suspend fun nextPollDelayMs(): Long {
        val interval = if (settings.current().onlyRefreshOnActivePage) ACTIVE_PAGE_INTERVAL_MS else PROFILE_INTERVAL_MS
        val current = _state.value
        val duration = current.track?.durationMs
        if (!current.isPlaying || duration == null) return interval
        // Refresh just after the current track ends so the next one shows up promptly.
        val untilEnd = (duration - current.positionAt(now())).toLong() + TRACK_END_GRACE_MS
        return untilEnd.coerceIn(MIN_POLL_INTERVAL_MS, interval)
    }

    private suspend fun poll() {
        val result = controller.snapshot()
        Log.d(TAG, "Poll: ${result.describe()}")
        if (commandsInFlight.get() > 0) return
        _state.update { current ->
            when (result) {
                is ApiResult.Ok -> PlayerReducer.applySnapshot(current, result.value, now())
                ApiResult.NoContent -> PlayerReducer.noPlayer(current)
                is ApiResult.Failure -> when (val error = result.error) {
                    is ApiError.NotLoggedIn -> PlayerReducer.notLoggedIn()
                    is ApiError.Offline -> PlayerReducer.refreshFailed(current, PlayerError("Offline", error.message))
                    else -> PlayerReducer.refreshFailed(current, PlayerError("Spotify error", error.message))
                }
            }
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
                val liked = (controller.isLiked(uri!!) as? ApiResult.Ok)?.value ?: return@collectLatest
                _state.update { if (it.track?.uri == uri && it.isLiked == null) it.copy(isLiked = liked) else it }
            }
    }

    private suspend fun prefetchUpcoming() {
        _state.map { it.track?.uri }
            .distinctUntilChanged()
            .filter { it != null }
            .collectLatest { uri ->
                val upcoming = (controller.upcoming() as? ApiResult.Ok)?.value ?: return@collectLatest
                _state.update { if (it.track?.uri == uri) it.copy(upcoming = upcoming) else it }
                val highRes = settings.current().highResThumbnails
                upcoming.mapNotNull { it.imageUrl(highRes) }.forEach(thumbnails::prefetch)
            }
    }

    private fun ApiResult<PlaybackSnapshot>.describe(): String = when (this) {
        is ApiResult.Ok -> "${if (value.isPlaying) "playing" else "paused"} ${value.track?.name} on ${value.deviceName}"
        ApiResult.NoContent -> "no active device"
        is ApiResult.Failure -> "failed: $error"
    }

    private companion object {
        const val ACTIVE_PAGE_INTERVAL_MS = 25_000L
        const val PROFILE_INTERVAL_MS = 45_000L
        const val MIN_POLL_INTERVAL_MS = 3_000L
        const val TRACK_END_GRACE_MS = 2_000L
        const val COMMAND_SETTLE_MS = 1_500L
    }
}
