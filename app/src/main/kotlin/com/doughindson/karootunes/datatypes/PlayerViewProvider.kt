package com.doughindson.karootunes.datatypes

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.doughindson.karootunes.KarooSystemServiceProvider
import com.doughindson.karootunes.R
import com.doughindson.karootunes.datatypes.actions.AddToLikedSongsAction
import com.doughindson.karootunes.datatypes.actions.NextAction
import com.doughindson.karootunes.datatypes.actions.PauseAction
import com.doughindson.karootunes.datatypes.actions.PlayAction
import com.doughindson.karootunes.datatypes.actions.PreviousAction
import com.doughindson.karootunes.datatypes.actions.ToggleOptionsMenuCallback
import com.doughindson.karootunes.datatypes.actions.ToggleShuffleAction
import com.doughindson.karootunes.screens.PlayActivity
import com.doughindson.karootunes.screens.KarooTunesSettings
import com.doughindson.karootunes.spotify.APIClient
import com.doughindson.karootunes.spotify.APIClientProvider
import com.doughindson.karootunes.spotify.PlayerAction
import com.doughindson.karootunes.spotify.PlayerState
import com.doughindson.karootunes.spotify.PlayerStateProvider
import com.doughindson.karootunes.spotify.ThumbnailCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import java.util.Locale

fun formatMs(ms: Int?): String {
    return ms?.let {
        val hours = it / 3600000
        val minutes = (it % 3600000) / 60000
        val seconds = (it % 60000) / 1000

        if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    } ?: "0:00"
}

/**
 * Compact fallback layout: used for SINGLE_FIELD - just track/artist text, play/next and a
 * thin progress bar.
 */
@Composable
fun PlayerWidgetCompact(
    context: Context,
    appState: PlayerState,
    playerSize: PlayerSize,
    baseDisabled: Boolean
) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(2.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            appState.isPlayingTrackName ?: "Unknown",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(Color.Black, Color.White)
            ),
            maxLines = 1
        )
        Text(
            appState.isPlayingArtistName ?: "Waiting for playback...",
            style = TextStyle(
                fontSize = 14.sp,
                color = ColorProvider(Color.Black, Color.White)
            ),
            maxLines = 1
        )

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(2.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
        ) {
            val playPauseAction = if (appState.isPlaying == true) PlayerAction.PAUSE else PlayerAction.PLAY
            PlayButton(appState, playerSize, baseDisabled || appState.pendingActions.contains(playPauseAction))

            val nextDisabled = baseDisabled || appState.pendingActions.contains(PlayerAction.SKIP_NEXT)
            Image(
                ImageProvider(R.drawable.skip_next_regular_132),
                "Next",
                modifier = GlanceModifier.size(40.dp).padding(10.dp)
                    .let { if (nextDisabled) it else it.clickable(actionRunCallback(NextAction::class.java)) },
                colorFilter = ColorFilter.tint(
                    ColorProvider(
                        Color.Black,
                        Color.White
                    )
                )
            )
        }

        val progress = (appState.playProgressInMs?.toFloat()
            ?: 0.0f) / (appState.currentTrackLengthInMs?.toFloat() ?: 1.0f)
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().padding(2.dp)
                .height(10.dp)
        )
    }
}

/**
 * A square utility button used in the right-hand column next to the album art (like/shuffle/
 * library). Sizing is driven entirely by the caller's modifier so the column can divide the
 * available height evenly between the three buttons.
 */
@Composable
private fun SquareUtilityButton(
    context: Context,
    @androidx.annotation.DrawableRes icon: Int,
    contentDescription: String,
    modifier: GlanceModifier,
    disabled: Boolean = false,
    toggledOff: Boolean = true,
    iconSize: Dp = 36.dp,
    actionProvider: () -> Action
) {
    // Active state (liked / shuffling) mirrors the play/pause button's green rather than plain
    // white, so the two active-state affordances read consistently at a glance.
    val tint = if (toggledOff) {
        ColorProvider(
            Color(context.getColor(R.color.lighterGray)),
            Color(context.getColor(R.color.lighterGray))
        )
    } else {
        ColorProvider(
            Color(context.getColor(R.color.spotifyGreen)),
            Color(context.getColor(R.color.spotifyGreen))
        )
    }

    Box(
        modifier = modifier
            .background(ImageProvider(R.drawable.round_button_bg_gray))
            .let { if (disabled) it else it.clickable(actionProvider()) },
        contentAlignment = Alignment.Center
    ) {
        Image(
            ImageProvider(icon),
            contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(tint)
        )
    }
}

/**
 * A transport button (previous/play-pause/next) for the bottom row. Unlike [SquareUtilityButton]
 * these fill whatever height the row is given via [GlanceModifier.defaultWeight], so the whole
 * row grows or shrinks with the space available on different Karoo screen sizes.
 */
@Composable
private fun TransportPillButton(
    @androidx.annotation.DrawableRes icon: Int,
    @androidx.annotation.DrawableRes background: Int,
    iconTint: androidx.glance.unit.ColorProvider,
    modifier: GlanceModifier,
    disabled: Boolean,
    iconSize: Dp = 36.dp,
    actionProvider: () -> Action
) {
    Box(
        modifier = modifier
            .background(ImageProvider(background))
            .let { if (disabled) it else it.clickable(actionProvider()) },
        contentAlignment = Alignment.Center
    ) {
        Image(
            ImageProvider(icon),
            "Transport control",
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconTint)
        )
    }
}

/**
 * The redesigned full-page "now playing" layout: large square album art with like/shuffle/
 * library buttons alongside it, track/artist text, a slim progress bar, and a bottom row of
 * previous/play-pause/next buttons that flexes to fill remaining vertical space.
 */
@Composable
fun PlayerWidgetFullPageRedesign(
    context: Context,
    appState: PlayerState,
    cachedThumbnail: Bitmap?,
    baseDisabled: Boolean,
    availableWidth: Dp
) {
    val mutedTextColor = ColorProvider(
        Color(context.getColor(R.color.lighterGray)),
        Color(context.getColor(R.color.lighterGray))
    )

    val outerPadding = 12.dp

    // The art + 3-button group must exactly fill the row's width while the art stays a perfect
    // square and the buttons stay square with even spacing. With buttonSize=b and gap=g (shared
    // by the horizontal art<->buttons gap and the gaps between stacked buttons):
    //   rowWidth = artSize + gap + buttonSize = (3b + 2g) + g + b = 4b + 3g
    // Fixing g = 0.15b (matching the original design's 15:100 gap:button ratio) gives:
    //   rowWidth = 4.45b  =>  b = rowWidth / 4.45
    // Floor (rather than round) so the sum of button/gap widths never exceeds the row's actual
    // pixel width after dp->px rounding - a fractional-dp overshoot here previously clipped the
    // right edge of the button column.
    val rowWidth = (availableWidth - outerPadding * 2).value
    val buttonSize = kotlin.math.floor(rowWidth / 4.45f).dp
    val gap = kotlin.math.floor(buttonSize.value * 0.15f).dp
    val artSize = buttonSize * 3 + gap * 2 // also the row's height, so art comes out square

    Column(modifier = GlanceModifier.fillMaxSize().padding(outerPadding)) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(artSize)) {
            Box(
                modifier = GlanceModifier.size(artSize)
                    .background(ImageProvider(R.drawable.round_button_bg_gray))
                    .cornerRadius(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // No icon here on purpose: a plain grey square reads clearly as "loading" the
                // instant the track changes, rather than a static icon that can look unchanged.
                if (cachedThumbnail != null) {
                    Image(
                        ImageProvider(cachedThumbnail),
                        "Album art",
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(gap))

            Column {
                val isLiked = appState.isTrackLiked == true
                SquareUtilityButton(
                    context,
                    if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                    if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                    GlanceModifier.size(buttonSize),
                    disabled = baseDisabled || appState.pendingActions.contains(PlayerAction.ADD_TO_LIBRARY),
                    toggledOff = !isLiked
                ) { actionRunCallback(AddToLikedSongsAction::class.java) }

                Spacer(modifier = GlanceModifier.height(gap))

                SquareUtilityButton(
                    context,
                    R.drawable.ic_shuffle,
                    "Shuffle",
                    GlanceModifier.size(buttonSize),
                    disabled = baseDisabled || appState.pendingActions.contains(PlayerAction.TOGGLE_SHUFFLE),
                    toggledOff = appState.isShuffling != true
                ) { actionRunCallback(ToggleShuffleAction::class.java) }

                Spacer(modifier = GlanceModifier.height(gap))

                SquareUtilityButton(
                    context,
                    R.drawable.ic_library_music,
                    "Library",
                    GlanceModifier.size(buttonSize),
                    disabled = baseDisabled
                ) { actionStartActivity(Intent(context, PlayActivity::class.java)) }
            }
        }

        Spacer(modifier = GlanceModifier.height(12.dp))

        // A skip button press optimistically substitutes the cached next/previous track's real
        // info (see NextAction/PreviousAction) - only fall back to a generic "Loading..." state
        // when no cached track was available to show instead.
        val isChangingTrack = appState.pendingActions.contains(PlayerAction.SKIP_NEXT) ||
            appState.pendingActions.contains(PlayerAction.SKIP_PREVIOUS)
        val showLoadingState = isChangingTrack && appState.isPlayingTrackName.isNullOrBlank() && appState.error == null
        val trackNameColor = if (showLoadingState) mutedTextColor else ColorProvider(Color.White, Color.White)

        val displayedTrackName = if (showLoadingState) {
            if (appState.pendingActions.contains(PlayerAction.SKIP_NEXT)) "Loading next track" else "Loading previous track"
        } else {
            appState.isPlayingTrackName ?: (appState.error?.title ?: "Unknown")
        }

        Text(
            displayedTrackName,
            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = trackNameColor),
            maxLines = 1
        )

        val artistName = if (appState.isPlayingArtistName.isNullOrBlank()) appState.isPlayingShowName else appState.isPlayingArtistName
        val displayedArtistName = if (showLoadingState) "" else (artistName ?: (appState.error?.message ?: "Waiting for playback..."))
        Text(
            displayedArtistName,
            style = TextStyle(fontSize = 15.sp, color = mutedTextColor),
            maxLines = if (appState.error == null) 1 else 3
        )

        Spacer(modifier = GlanceModifier.height(10.dp))

        val progress = (appState.playProgressInMs?.toFloat() ?: 0f) / (appState.currentTrackLengthInMs?.toFloat() ?: 1f)
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(8.dp),
            color = ColorProvider(
                Color(context.getColor(R.color.progressFillGray)),
                Color(context.getColor(R.color.progressFillGray))
            ),
            backgroundColor = ColorProvider(
                Color(context.getColor(R.color.darkerGray)),
                Color(context.getColor(R.color.darkerGray))
            )
        )

        Spacer(modifier = GlanceModifier.height(12.dp))

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            TransportPillButton(
                icon = R.drawable.ic_skip_previous,
                background = R.drawable.round_button_bg_gray,
                iconTint = mutedTextColor,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                disabled = baseDisabled || appState.pendingActions.contains(PlayerAction.SKIP_PREVIOUS)
            ) { actionRunCallback(PreviousAction::class.java) }

            Spacer(modifier = GlanceModifier.width(10.dp))

            val playPauseAction = if (appState.isPlaying == true) PlayerAction.PAUSE else PlayerAction.PLAY
            TransportPillButton(
                icon = if (appState.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                background = R.drawable.round_button_bg_green,
                iconTint = ColorProvider(
                    Color(context.getColor(R.color.black)),
                    Color(context.getColor(R.color.black))
                ),
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                disabled = baseDisabled || appState.pendingActions.contains(playPauseAction)
            ) {
                if (appState.isPlaying == true) actionRunCallback(PauseAction::class.java) else actionRunCallback(PlayAction::class.java)
            }

            Spacer(modifier = GlanceModifier.width(10.dp))

            TransportPillButton(
                icon = R.drawable.ic_skip_next,
                background = R.drawable.round_button_bg_gray,
                iconTint = mutedTextColor,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                disabled = baseDisabled || appState.pendingActions.contains(PlayerAction.SKIP_NEXT)
            ) { actionRunCallback(NextAction::class.java) }
        }
    }
}

/**
 * The mid-size ("MEDIUM"/"SMALL") layout: track info + a small options-toggle button by default,
 * swapping to like/shuffle/library buttons for a few seconds after the toggle is tapped (see
 * [PlayerState.isInOptionsMenu] and KarooTunesServices.startOptionsMenuAutoCollapseJob), with the
 * same progress bar + transport row as [PlayerWidgetFullPageRedesign] underneath.
 */
@Composable
fun PlayerWidgetMidPageRedesign(
    context: Context,
    appState: PlayerState,
    baseDisabled: Boolean,
    availableWidth: Dp,
    availableHeight: Dp
) {
    val mutedTextColor = ColorProvider(
        Color(context.getColor(R.color.lighterGray)),
        Color(context.getColor(R.color.lighterGray))
    )

    val outerPadding = 12.dp
    val verticalGap = 10.dp
    val progressBarHeight = 8.dp

    val contentWidth = availableWidth - outerPadding * 2
    val remainingHeight = availableHeight - outerPadding * 2 - verticalGap * 2 - progressBarHeight

    // Top:bottom row ratio is 100:140 = 5:7 of the remaining height, matching the design mockup.
    // Floor the top row; the bottom row absorbs the rounding remainder by subtraction - same
    // floor-not-round discipline that fixed a real button-clipping bug in the FULL_PAGE layout.
    val topRowHeight = kotlin.math.floor(remainingHeight.value * 5f / 12f).dp
    val bottomRowHeight = remainingHeight - topRowHeight

    // FULL_PAGE's buttons are big enough that a fixed 36dp icon always fits; at MEDIUM/SMALL
    // sizes the rows can be much shorter, so a fixed icon would overflow its own button - scale
    // it down (never up) to fit.
    val topIconSize = kotlin.math.min(36f, topRowHeight.value * 0.55f).dp
    val bottomIconSize = kotlin.math.min(36f, bottomRowHeight.value * 0.4f).dp

    Column(modifier = GlanceModifier.fillMaxSize().padding(outerPadding)) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(topRowHeight),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            if (appState.isInOptionsMenu) {
                // Three equal-ish rectangles fill the same row the track info normally occupies.
                val gapB = kotlin.math.floor(topRowHeight.value * 0.12f).dp
                val buttonWidthB = kotlin.math.floor((contentWidth.value - gapB.value * 2f) / 3f).dp
                val lastButtonWidthB = contentWidth - gapB * 2 - buttonWidthB * 2

                val isLiked = appState.isTrackLiked == true
                SquareUtilityButton(
                    context,
                    if (isLiked) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
                    if (isLiked) "Remove from Liked Songs" else "Add to Liked Songs",
                    GlanceModifier.size(buttonWidthB, topRowHeight),
                    disabled = baseDisabled || appState.pendingActions.contains(PlayerAction.ADD_TO_LIBRARY),
                    toggledOff = !isLiked,
                    iconSize = topIconSize
                ) { actionRunCallback(AddToLikedSongsAction::class.java) }

                Spacer(modifier = GlanceModifier.width(gapB))

                SquareUtilityButton(
                    context,
                    R.drawable.ic_shuffle,
                    "Shuffle",
                    GlanceModifier.size(buttonWidthB, topRowHeight),
                    disabled = baseDisabled || appState.pendingActions.contains(PlayerAction.TOGGLE_SHUFFLE),
                    toggledOff = appState.isShuffling != true,
                    iconSize = topIconSize
                ) { actionRunCallback(ToggleShuffleAction::class.java) }

                Spacer(modifier = GlanceModifier.width(gapB))

                SquareUtilityButton(
                    context,
                    R.drawable.ic_library_music,
                    "Library",
                    GlanceModifier.size(lastButtonWidthB, topRowHeight),
                    disabled = baseDisabled,
                    iconSize = topIconSize
                ) { actionStartActivity(Intent(context, PlayActivity::class.java)) }
            } else {
                // A skip button press optimistically substitutes the cached next/previous track's
                // real info (see NextAction/PreviousAction) - only fall back to a generic
                // "Loading..." state when no cached track was available to show instead.
                val gapA = kotlin.math.floor(topRowHeight.value * 0.15f).dp

                val isChangingTrack = appState.pendingActions.contains(PlayerAction.SKIP_NEXT) ||
                    appState.pendingActions.contains(PlayerAction.SKIP_PREVIOUS)
                val showLoadingState = isChangingTrack && appState.isPlayingTrackName.isNullOrBlank() && appState.error == null
                val trackNameColor = if (showLoadingState) mutedTextColor else ColorProvider(Color.White, Color.White)

                val displayedTrackName = if (showLoadingState) {
                    if (appState.pendingActions.contains(PlayerAction.SKIP_NEXT)) "Loading next track" else "Loading previous track"
                } else {
                    appState.isPlayingTrackName ?: (appState.error?.title ?: "Unknown")
                }

                val artistName = if (appState.isPlayingArtistName.isNullOrBlank()) appState.isPlayingShowName else appState.isPlayingArtistName
                val displayedArtistName = if (showLoadingState) "" else (artistName ?: (appState.error?.message ?: "Waiting for playback..."))

                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        displayedTrackName,
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = trackNameColor),
                        maxLines = 1
                    )

                    Text(
                        displayedArtistName,
                        style = TextStyle(fontSize = 15.sp, color = mutedTextColor),
                        maxLines = if (appState.error == null) 1 else 3
                    )
                }

                Spacer(modifier = GlanceModifier.width(gapA))

                SquareUtilityButton(
                    context,
                    R.drawable.dots_vertical_rounded_regular_132,
                    "Show options",
                    GlanceModifier.size(topRowHeight),
                    disabled = baseDisabled,
                    iconSize = topIconSize
                ) { actionRunCallback(ToggleOptionsMenuCallback::class.java) }
            }
        }

        Spacer(modifier = GlanceModifier.height(verticalGap))

        val progress = (appState.playProgressInMs?.toFloat() ?: 0f) / (appState.currentTrackLengthInMs?.toFloat() ?: 1f)
        LinearProgressIndicator(
            progress = progress,
            modifier = GlanceModifier.fillMaxWidth().height(progressBarHeight),
            color = ColorProvider(
                Color(context.getColor(R.color.progressFillGray)),
                Color(context.getColor(R.color.progressFillGray))
            ),
            backgroundColor = ColorProvider(
                Color(context.getColor(R.color.darkerGray)),
                Color(context.getColor(R.color.darkerGray))
            )
        )

        Spacer(modifier = GlanceModifier.height(verticalGap))

        Row(
            modifier = GlanceModifier.fillMaxWidth().height(bottomRowHeight),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            val gapBottom = kotlin.math.floor(bottomRowHeight.value * 0.15f).dp
            val usableBottomWidth = contentWidth - gapBottom * 2
            val sideWidth = kotlin.math.floor(usableBottomWidth.value * 0.325f).dp
            val centerWidth = usableBottomWidth - sideWidth * 2

            TransportPillButton(
                icon = R.drawable.ic_skip_previous,
                background = R.drawable.round_button_bg_gray,
                iconTint = mutedTextColor,
                modifier = GlanceModifier.size(sideWidth, bottomRowHeight),
                disabled = baseDisabled || appState.pendingActions.contains(PlayerAction.SKIP_PREVIOUS),
                iconSize = bottomIconSize
            ) { actionRunCallback(PreviousAction::class.java) }

            Spacer(modifier = GlanceModifier.width(gapBottom))

            val playPauseAction = if (appState.isPlaying == true) PlayerAction.PAUSE else PlayerAction.PLAY
            TransportPillButton(
                icon = if (appState.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                background = R.drawable.round_button_bg_green,
                iconTint = ColorProvider(
                    Color(context.getColor(R.color.black)),
                    Color(context.getColor(R.color.black))
                ),
                modifier = GlanceModifier.size(centerWidth, bottomRowHeight),
                disabled = baseDisabled || appState.pendingActions.contains(playPauseAction),
                iconSize = bottomIconSize
            ) {
                if (appState.isPlaying == true) actionRunCallback(PauseAction::class.java) else actionRunCallback(PlayAction::class.java)
            }

            Spacer(modifier = GlanceModifier.width(gapBottom))

            TransportPillButton(
                icon = R.drawable.ic_skip_next,
                background = R.drawable.round_button_bg_gray,
                iconTint = mutedTextColor,
                modifier = GlanceModifier.size(sideWidth, bottomRowHeight),
                disabled = baseDisabled || appState.pendingActions.contains(PlayerAction.SKIP_NEXT),
                iconSize = bottomIconSize
            ) { actionRunCallback(NextAction::class.java) }
        }
    }
}

class PlayerViewProvider(private val apiClientProvider: APIClientProvider,
                         private val thumbnailCache: ThumbnailCache,
                         private val playerStateProvider: PlayerStateProvider,
                         private val context: Context,
                         private val karooSystemServiceProvider: KarooSystemServiceProvider
) {

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    private val glance = GlanceRemoteViews()

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    fun provideView(playerSize: PlayerSize, viewSizePx: Pair<Int, Int>): Flow<RemoteViews> = flow {
        data class StreamData(
            val playerState: PlayerState,
            val apiClient: APIClient,
            val settings: KarooTunesSettings
        )

        // Glance needs a real size here (exposed to composables via LocalSize) so that fillMaxSize
        // etc. have something concrete to measure against - passing DpSize.Unspecified was the
        // actual cause of the artwork/button layout stretching unpredictably on-device.
        val density = context.resources.displayMetrics.density
        val dpSize = DpSize((viewSizePx.first / density).dp, (viewSizePx.second / density).dp)

        combine(playerStateProvider.state, apiClientProvider.getActiveAPIInstance(), karooSystemServiceProvider.streamSettings()) { state, apiClient, settings ->
                StreamData(state, apiClient, settings)
            }
            .collect { (appState, _, settings) ->
                val thumbnailUrl = when (playerSize) {
                    PlayerSize.SINGLE_FIELD, PlayerSize.SMALL, PlayerSize.MEDIUM -> null
                    PlayerSize.FULL_PAGE -> {
                        if (settings.highResThumbnails) {
                            appState.isPlayingTrackThumbnailUrls?.getOrNull(1) ?: appState.isPlayingTrackThumbnailUrls?.last()
                        } else {
                            appState.isPlayingTrackThumbnailUrls?.last()
                        }
                    }
                }

                val cachedThumbnail = thumbnailUrl?.let { url ->
                    thumbnailCache.ensureThumbnailIsInCache(url)
                    thumbnailCache.getThumbnailFromInMemoryCache(url)
                }

                val baseDisabled = appState.isPlaying == null

                val result =
                    if (playerSize == PlayerSize.FULL_PAGE) {
                        glance.compose(context, dpSize) {
                            PlayerWidgetFullPageRedesign(context, appState, cachedThumbnail, baseDisabled, dpSize.width)
                        }
                    } else if (playerSize == PlayerSize.MEDIUM || playerSize == PlayerSize.SMALL) {
                        glance.compose(context, dpSize) {
                            PlayerWidgetMidPageRedesign(context, appState, baseDisabled, dpSize.width, dpSize.height)
                        }
                    } else {
                        glance.compose(context, dpSize) {
                            PlayerWidgetCompact(context, appState, playerSize, baseDisabled)
                        }
                    }

                emit(result.remoteViews)
            }
    }

}
