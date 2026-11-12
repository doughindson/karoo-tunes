package com.doughindson.karootunes.widget

import androidx.annotation.StringRes
import com.doughindson.karootunes.R
import com.doughindson.karootunes.playback.Connection
import com.doughindson.karootunes.playback.MediaType
import com.doughindson.karootunes.playback.PlayerCommand
import com.doughindson.karootunes.playback.PlayerReducer
import com.doughindson.karootunes.playback.PlayerState
import com.doughindson.karootunes.playback.RepeatMode
import com.doughindson.karootunes.playback.WidgetUiState

sealed interface WidgetText {
    data class Literal(val text: String) : WidgetText
    data class Resource(@StringRes val id: Int) : WidgetText
}

/**
 * Exactly what one widget layout draws. Built from the player state, compared before every render,
 * so the widget only recomposes when something visible changed.
 */
data class WidgetModel(
    val layout: PlayerLayout,
    val title: WidgetText,
    val subtitle: WidgetText?,
    val titleMuted: Boolean,
    val subtitleMaxLines: Int,
    val isPlaying: Boolean,
    val isEpisode: Boolean,
    /** 0..1, or null when the layout has no progress bar or the duration is unknown. */
    val progress: Float?,
    val optionsOpen: Boolean,
    val liked: Boolean,
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val enabled: Set<PlayerCommand>,
    val artworkUrl: String?,
) {
    fun isEnabled(command: PlayerCommand) = command in enabled

    companion object {
        fun from(
            state: PlayerState,
            ui: WidgetUiState,
            layout: PlayerLayout,
            highResArtwork: Boolean,
            now: Long,
        ): WidgetModel {
            val track = state.track
            val error = state.error
            val changingTrack = ui.pending.any { it == PlayerCommand.NEXT || it == PlayerCommand.PREVIOUS }

            val title: WidgetText
            val subtitle: WidgetText?
            var muted = false
            var subtitleLines = 1
            when {
                track != null -> {
                    title = track.name?.takeIf { it.isNotBlank() }?.let { WidgetText.Literal(it) } ?: WidgetText.Resource(R.string.widget_unknown_track)
                    subtitle = when {
                        error != null -> WidgetText.Literal("${error.title}: ${error.message}")
                        else -> track.subtitle?.let { WidgetText.Literal(it) }
                    }
                }
                changingTrack && state.hasPlayer -> {
                    title = WidgetText.Resource(
                        if (PlayerCommand.NEXT in ui.pending) R.string.widget_loading_next else R.string.widget_loading_previous
                    )
                    subtitle = null
                    muted = true
                }
                state.hasPlayer && error == null -> {
                    title = WidgetText.Resource(R.string.widget_loading)
                    subtitle = null
                    muted = true
                }
                error != null -> {
                    title = WidgetText.Literal(error.title)
                    subtitle = WidgetText.Literal(error.message)
                    subtitleLines = 3
                }
                else -> {
                    title = WidgetText.Resource(R.string.widget_connecting)
                    subtitle = null
                    muted = state.connection == Connection.UNKNOWN
                }
            }

            // Buttons stay active while a command is in flight: through the companion app an answer
            // takes seconds, and greyed-out buttons made every press feel stuck. Repeat taps of a
            // pending command are ignored in PlayerCommands instead.
            val enabled = PlayerCommand.entries.filterTo(mutableSetOf()) { command -> PlayerReducer.isAllowed(state, command) }

            val duration = track?.durationMs
            val progress = if (layout.showsProgress && duration != null && duration > 0) {
                (state.positionAt(now).toFloat() / duration).coerceIn(0f, 1f).roundTo(PROGRESS_STEPS)
            } else {
                null
            }

            return WidgetModel(
                layout = layout,
                title = title,
                subtitle = subtitle,
                titleMuted = muted,
                subtitleMaxLines = subtitleLines,
                isPlaying = state.isPlaying,
                isEpisode = track?.type == MediaType.EPISODE,
                progress = progress,
                optionsOpen = ui.optionsOpen,
                liked = state.isLiked == true,
                shuffle = state.shuffle,
                repeat = state.repeat,
                enabled = enabled,
                artworkUrl = if (layout.showsArtwork) track?.imageUrl(highResArtwork) else null,
            )
        }

        /** Coarse enough that small position corrections from Spotify don't cause a re-render. */
        private const val PROGRESS_STEPS = 200

        private fun Float.roundTo(steps: Int): Float = kotlin.math.round(this * steps) / steps
    }
}
