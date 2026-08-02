package com.doughindson.karootunes

import android.content.Context
import android.util.Log
import com.doughindson.karootunes.KarooTunesExtension.Companion.TAG
import com.doughindson.karootunes.datatypes.PlayerDataType
import com.doughindson.karootunes.screens.KarooTunesSettings
import com.doughindson.karootunes.spotify.APIClientProvider
import com.doughindson.karootunes.spotify.CachedTrackInfo
import com.doughindson.karootunes.spotify.LocalClient
import com.doughindson.karootunes.spotify.PlaybackType
import com.doughindson.karootunes.spotify.PlayerAction
import com.doughindson.karootunes.spotify.PlayerInPreviewModeProvider
import com.doughindson.karootunes.spotify.PlayerState
import com.doughindson.karootunes.spotify.PlayerStateProvider
import com.doughindson.karootunes.spotify.RepeatState
import com.doughindson.karootunes.spotify.ThumbnailCache
import com.doughindson.karootunes.spotify.WebAPIClient
import io.hammerhead.karooext.models.ActiveRideProfile
import io.hammerhead.karooext.models.RideState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.time.TimeSource

class KarooTunesServices(private val webAPIClient: WebAPIClient,
                             private val apiClientProvider: APIClientProvider,
                             private val thumbnailCache: ThumbnailCache,
                             private val autoVolume: AutoVolume,
                             private val localClient: LocalClient,
                             private val playerStateProvider: PlayerStateProvider,
                             private val playerInPreviewModeProvider: PlayerInPreviewModeProvider,
                             private val context: Context,
                             private val karooSystem: KarooSystemServiceProvider) {

    private val scope = CoroutineScope(Dispatchers.IO)

    fun startJobs(){
        scope.launch {
            supervisorScope {
                launch { startPlayerRefreshJob() }
                launch { startPlayerAdvanceJob() }
                launch { startThumbnailCleanupJob() }
                launch { startLocalSpotifyJob() }
                launch { startAutoVolumeJob() }
                launch { startOptionsMenuAutoCollapseJob() }
            }
        }
    }

    fun cancelJobs(){
        scope.cancel()
    }

    private suspend fun startAutoVolumeJob() {
        autoVolume.setAutoVolume()
    }

    // Auto-collapses the MEDIUM/SMALL widget's expanded like/shuffle/library panel 3s after it's
    // opened. collectLatest cancels the pending delay whenever isInOptionsMenu changes again
    // before it fires (re-toggle, or Next/Previous force-closing it) - avoiding a stale revert
    // from a naively per-tap-launched coroutine firing after a fresh re-open already began.
    private suspend fun startOptionsMenuAutoCollapseJob() {
        playerStateProvider.state
            .map { it.isInOptionsMenu }
            .distinctUntilChanged()
            .collectLatest { isInOptionsMenu ->
                if (isInOptionsMenu) {
                    delay(3_000)
                    playerStateProvider.update { it.copy(isInOptionsMenu = false) }
                }
            }
    }

    private suspend fun startPlayerRefreshJob() {
        apiClientProvider.streamLocalSpotifyIsEnabled().collectLatest {
            Log.d(TAG, "Local Spotify enabled: $it")

            if (it) {
                refreshLocalPlayer()
            } else {
                refreshWebPlayer()
            }
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun refreshLocalPlayer() {
        Log.d(TAG, "Getting local player state")

        localClient.streamState()
            .debounce(500L)
            .collect { (playerError, playerState) ->
                val newTrackUri = playerState?.track?.uri
                val trackChanged = newTrackUri != playerStateProvider.state.value.isPlayingTrackUri
                val newIsTrackLiked = if (trackChanged || playerStateProvider.state.value.isTrackLiked == null) {
                    newTrackUri?.let { uri -> localClient.isTrackSaved(uri) }
                } else {
                    playerStateProvider.state.value.isTrackLiked
                }

                // Snapshot the track we're leaving so a subsequent Previous press can show it
                // instantly instead of a generic loading state (no queue-prefetch API is
                // available in local/App Remote mode, so upcomingTracksCache stays empty here).
                val confirmedPreviousTrack = if (trackChanged) {
                    playerStateProvider.state.value.isPlayingTrackUri?.let {
                        CachedTrackInfo(
                            trackUri = playerStateProvider.state.value.isPlayingTrackUri,
                            trackName = playerStateProvider.state.value.isPlayingTrackName,
                            artistName = playerStateProvider.state.value.isPlayingArtistName,
                            thumbnailUrls = playerStateProvider.state.value.isPlayingTrackThumbnailUrls,
                            durationMs = playerStateProvider.state.value.currentTrackLengthInMs
                        )
                    }
                } else null

                playerStateProvider.update { state ->
                    val disabled = mutableMapOf<PlayerAction, Boolean>()

                    disabled[PlayerAction.SET_VOLUME] = false

                    state.copy(
                        isPlayingType = if (playerState?.track?.isEpisode == true) PlaybackType.EPISODE else PlaybackType.TRACK,
                        isPlayingTrackName = playerState?.track?.name,
                        isPlayingTrackUri = playerState?.track?.uri,
                        isPlayingArtistName = playerState?.track?.artists?.joinToString(", ") {
                            it.name ?: ""
                        },
                        isPlayingShowName = if(playerState?.track?.isEpisode == true || playerState?.track?.isPodcast == true) playerState.track?.album?.name else null,
                        isPlayingTrackThumbnailUrls = playerState?.track?.imageUri?.raw?.let {
                            Log.d(TAG, "Thumbnail URL: $it")
                            listOf(it)
                        },
                        isShuffling = playerState?.playbackOptions?.isShuffling,
                        isRepeating = playerState?.playbackOptions?.repeatMode?.let { RepeatState.fromInt(it) },
                        isInitialized = true,
                        currentTrackLengthInMs = playerState?.track?.duration?.toInt(),
                        playProgressInMs = playerState?.playbackPosition?.toInt(),
                        isPlaying = playerState?.track?.name?.let { !playerState.isPaused },
                        commandPending = false,
                        isLocalPlayer = true,
                        disabledActions = disabled,
                        volume = localClient.getVolume(),
                        isTrackLiked = newIsTrackLiked,
                        previousTrackCache = confirmedPreviousTrack ?: state.previousTrackCache,
                        error = playerError
                    )
                }
            }
    }

    /**
     * Emits one value when the player has reached the end of the current track or the user has issued a new command
     */
    private fun playerExhaustedStream(): Flow<Unit> = flow {
        var isExhausted = false

        playerStateProvider.state.collect { state ->
            val newValue = (state.isPlaying == true
                    && state.playProgressInMs != null
                    && state.currentTrackLengthInMs != null
                    && state.playProgressInMs >= state.currentTrackLengthInMs) || state.commandPending

            if (newValue && !isExhausted){
                delay(3_000)
                emit(Unit)
            }

            isExhausted = newValue
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun refreshWebPlayer() {
        val tickerFlow = flow {
            emit(Unit)
            emitAll(playerExhaustedStream())
        }

        val refreshRequestedFlow = tickerFlow.flatMapLatest {
            karooSystem.streamSettings().flatMapLatest { settings ->
                val delayMs = if (settings.onlyRefreshOnActivePage) REFRESH_INTERVAL_ACTIVE_PAGE_MS else REFRESH_INTERVAL_DEFAULT_MS
                flow {
                    while (true) {
                        emit(Unit)
                        delay(delayMs)
                    }
                }
            }
        }

        data class StreamData(
            val isVisible: Boolean,
            val rideState: RideState,
            val rideProfile: ActiveRideProfile?,
            val settings: KarooTunesSettings,
            val playerInPreviewMode: Int,
            val playerState: PlayerState
        )

        val refreshRequestedFlowActiveOrPreview = combine(karooSystem.karooSystemService.streamDatatypeIsVisible(PlayerDataType.DATA_TYPE_ID),
            karooSystem.streamRideState(),
            karooSystem.karooSystemService.streamActiveRideProfile(),
            karooSystem.streamSettings(),
            playerInPreviewModeProvider.state,
            playerStateProvider.state
        ) { args ->
            val isVisible = args[0] as Boolean
            val rideState = args[1] as RideState
            val rideProfile = args[2] as ActiveRideProfile?
            val settings = args[3] as KarooTunesSettings
            val playerInPreviewMode = args[4] as PlayerInPreviewModeProvider.PlayerInPreviewModeState
            val playerState = args[5] as PlayerState

            StreamData(isVisible, rideState, rideProfile, settings, playerInPreviewMode.inPreviewMode, playerState)
        }.distinctUntilChanged().map { streamData ->
            if (streamData.settings.onlyRefreshOnActivePage) {
                Log.d(TAG, "Player visibility update: isVisible=${streamData.isVisible}, playerInPreviewMode=${streamData.playerInPreviewMode}, rideState=${streamData.rideState}")

                streamData.playerInPreviewMode > 0 || streamData.isVisible || streamData.playerState.commandPending
            } else {
                val datatypesOnCurrentProfile = streamData.rideProfile?.profile?.pages
                    ?.flatMap { it.elements }
                    ?.map { it.dataTypeId }
                    ?.toSet() ?: emptySet()

                val isOnCurrentProfile = datatypesOnCurrentProfile.contains(PlayerDataType.DATA_TYPE_ID)

                Log.d(TAG, "Player visibility update: playerInPreviewMode=${streamData.playerInPreviewMode}, isOnCurrentProfile=$isOnCurrentProfile, rideState=${streamData.rideState}")

                streamData.playerInPreviewMode > 0 || isOnCurrentProfile || streamData.playerState.commandPending
            }
        }.distinctUntilChanged()

        val refreshFlow = combine(refreshRequestedFlow, refreshRequestedFlowActiveOrPreview) { _, activeOrPreview ->
            activeOrPreview
        }.filter { it }.throttle(10_000L)

        refreshFlow.collect {
            try {
                Log.d(TAG, "Getting player state")
                val start = TimeSource.Monotonic.markNow()
                val playerState = webAPIClient.getPlayerState()
                val runtime = TimeSource.Monotonic.markNow() - start
                Log.d(TAG, "Got player state in $runtime: $playerState")

                val disabledActions = mutableMapOf<PlayerAction, Boolean>()
                playerState?.device?.supportsVolume?.let { volumeSupported -> disabledActions.put(
                    PlayerAction.SET_VOLUME, !volumeSupported) }
                playerState?.actions?.disallows?.let { disallows ->
                    disallows.pausing?.let { disabledActions.put(PlayerAction.PAUSE, it) }
                    disallows.seeking?.let { disabledActions.put(PlayerAction.SEEK, it) }
                    disallows.skippingNext?.let { disabledActions.put(PlayerAction.SKIP_NEXT, it) }
                    disallows.skippingPrev?.let { disabledActions.put(PlayerAction.SKIP_PREVIOUS, it) }
                    disallows.togglingRepeatContext?.let { disabledActions.put(PlayerAction.TOGGLE_REPEAT, it) }
                    disallows.togglingRepeatTrack?.let { disabledActions.put(PlayerAction.TOGGLE_REPEAT, it) }
                    disallows.resuming?.let { disabledActions.put(PlayerAction.PLAY, it) }
                    disallows.togglingShuffle?.let { disabledActions.put(PlayerAction.TOGGLE_SHUFFLE, it) }
                }

                val newTrackId = playerState?.item?.id
                val trackChanged = newTrackId != playerStateProvider.state.value.isPlayingTrackId
                val newIsTrackLiked = if (trackChanged || playerStateProvider.state.value.isTrackLiked == null) {
                    newTrackId?.let { id -> webAPIClient.isTrackSaved(id) }
                } else {
                    playerStateProvider.state.value.isTrackLiked
                }

                // Snapshot the track we're leaving (before it's overwritten below) so a subsequent
                // Previous press can show it instantly instead of a generic loading state.
                val confirmedPreviousTrack = if (trackChanged) {
                    playerStateProvider.state.value.isPlayingTrackId?.let { oldState ->
                        CachedTrackInfo(
                            trackId = playerStateProvider.state.value.isPlayingTrackId,
                            trackUri = playerStateProvider.state.value.isPlayingTrackUri,
                            trackName = playerStateProvider.state.value.isPlayingTrackName,
                            artistName = playerStateProvider.state.value.isPlayingArtistName,
                            thumbnailUrls = playerStateProvider.state.value.isPlayingTrackThumbnailUrls,
                            durationMs = playerStateProvider.state.value.currentTrackLengthInMs
                        )
                    }
                } else null

                val shouldRefreshQueue = trackChanged || playerStateProvider.state.value.upcomingTracksCache.isEmpty()
                val newUpcomingTracks = if (shouldRefreshQueue) {
                    val settings = karooSystem.streamSettings().first()

                    webAPIClient.getPlayerQueue()?.queue?.take(3)?.map { queueItem ->
                        val images = queueItem.images ?: queueItem.album?.images
                        val url = if (settings.highResThumbnails) {
                            images?.getOrNull(1)?.url ?: images?.lastOrNull()?.url
                        } else {
                            images?.lastOrNull()?.url
                        }
                        url?.let { thumbnailCache.ensureThumbnailIsInCache(it) }

                        CachedTrackInfo(
                            trackId = queueItem.id,
                            trackUri = queueItem.uri,
                            trackName = queueItem.name,
                            artistName = queueItem.artists?.joinToString(", ") { artist -> artist.name ?: "" },
                            thumbnailUrls = images?.mapNotNull { it.url },
                            durationMs = queueItem.durationMs
                        )
                    } ?: playerStateProvider.state.value.upcomingTracksCache
                } else {
                    playerStateProvider.state.value.upcomingTracksCache
                }

                playerStateProvider.update { state ->
                    state.copy(
                        isPlayingType = PlaybackType.fromString(playerState?.currentlyPlayingType),
                        isPlayingTrackName = playerState?.item?.name,
                        isPlayingTrackId = playerState?.item?.id,
                        isPlayingArtistName = playerState?.item?.artists?.joinToString(", ") {
                            it.name ?: ""
                        },
                        isPlayingShowName = playerState?.item?.show?.getItemName(),
                        isPlayingTrackThumbnailUrls = (playerState?.item?.images ?: playerState?.item?.album?.images)?.mapNotNull { it.url },
                        isShuffling = playerState?.shuffleState,
                        isRepeating = RepeatState.fromString(playerState?.repeatState),
                        isInitialized = true,
                        currentTrackLengthInMs = playerState?.item?.durationMs,
                        playProgressInMs = playerState?.progressMs,
                        isPlaying = playerState?.isPlaying,
                        commandPending = false,
                        isLocalPlayer = false,
                        disabledActions = disabledActions,
                        volume = playerState?.device?.volumePercent?.toFloat()?.div(100f)?.coerceIn(0f, 1f),
                        isTrackLiked = newIsTrackLiked,
                        previousTrackCache = confirmedPreviousTrack ?: state.previousTrackCache,
                        upcomingTracksCache = newUpcomingTracks,
                        error = if (playerState != null) null else state.error
                    )
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to get player state: ${e.message}", e)
            }
        }
    }

    private suspend fun startPlayerAdvanceJob() {
        // Advances the progress bar locally between real polls (no network call here) - ticking
        // every second instead of every 5s makes it visibly smoother. Advancing by the actual
        // elapsed time (rather than a flat 1000ms) avoids drift if a tick is ever delayed.
        var lastTick = TimeSource.Monotonic.markNow()

        while(true) {
            try {
                delay(1_000)

                val now = TimeSource.Monotonic.markNow()
                val elapsedMs = (now - lastTick).inWholeMilliseconds
                lastTick = now

                playerStateProvider.update { player ->
                    if (player.isPlaying == true) {
                        val currentProgress = player.playProgressInMs ?: 0
                        val length = player.currentTrackLengthInMs ?: 0

                        player.copy(playProgressInMs = (currentProgress + elapsedMs.toInt()).coerceAtMost(length))
                    } else {
                        player
                    }
                }
            } catch(e: CancellationException){
                Log.w(TAG, "Player advance job was cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update player progress", e)
            }
        }
    }

    private suspend fun startThumbnailCleanupJob() {
        while(true){
            try {
                thumbnailCache.clearCache()
            } catch (e: Exception){
                Log.e(TAG, "Failed to clean up thumbnail cache", e)
            }

            delay(60 * 60 * 1_000)
        }
    }

    private suspend fun startLocalSpotifyJob() {
        karooSystem.streamSettings()
            .distinctUntilChangedBy { it.useLocalSpotifyIfAvailable }
            .collectLatest { settings ->
                // Disconnect if local connection is still active
                try {
                    localClient.disconnect()
                } catch(e: Throwable){
                    Log.e(TAG, "Failed to disconnect local Spotify client", e)
                }

                // If the user has disabled local Spotify, we don't need to do anything
                if (!settings.useLocalSpotifyIfAvailable) awaitCancellation()

                while(true){
                    val connectionState = localClient.initialize()
                    Log.d(TAG, "Local Spotify connection state: $connectionState")

                    delay(1_000L * 60)
                }
            }
    }

    companion object {
        const val REFRESH_INTERVAL_ACTIVE_PAGE_MS = 25 * 1_000L
        const val REFRESH_INTERVAL_DEFAULT_MS = 45 * 1_000L
    }
}