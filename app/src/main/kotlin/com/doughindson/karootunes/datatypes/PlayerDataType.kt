package com.doughindson.karootunes.datatypes

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import com.doughindson.karootunes.KarooTunesExtension
import com.doughindson.karootunes.KarooSystemServiceProvider
import com.doughindson.karootunes.R
import com.doughindson.karootunes.datatypes.actions.FastForwardAction
import com.doughindson.karootunes.datatypes.actions.LouderAction
import com.doughindson.karootunes.datatypes.actions.NextAction
import com.doughindson.karootunes.datatypes.actions.PauseAction
import com.doughindson.karootunes.datatypes.actions.PlayAction
import com.doughindson.karootunes.datatypes.actions.PreviousAction
import com.doughindson.karootunes.datatypes.actions.QuieterAction
import com.doughindson.karootunes.datatypes.actions.RewindAction
import com.doughindson.karootunes.datatypes.actions.ToggleOptionsMenuCallback
import com.doughindson.karootunes.datatypes.actions.ToggleRepeatAction
import com.doughindson.karootunes.datatypes.actions.ToggleShuffleAction
import com.doughindson.karootunes.screens.PlayActivity
import com.doughindson.karootunes.screens.QueueActivity
import com.doughindson.karootunes.spotify.PlayerAction
import com.doughindson.karootunes.spotify.PlayerInPreviewModeProvider
import com.doughindson.karootunes.spotify.PlayerState
import com.doughindson.karootunes.spotify.RepeatState
import com.doughindson.karootunes.streamDatatypeIsVisible
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Composable fun ActionButton(
    @DrawableRes icon: Int,
    disabled: Boolean,
    toggledOff: Boolean = false,
    actionProvider: () -> Action
) {
    val context = LocalContext.current

    val colorFilter = if (disabled){
        ColorFilter.tint(ColorProvider(Color(ContextCompat.getColor(context, R.color.lighterGray)), Color(ContextCompat.getColor(context, R.color.darkerGray))))
    } else if (toggledOff){
        ColorFilter.tint(ColorProvider(Color(ContextCompat.getColor(context, R.color.lightGray)), Color(ContextCompat.getColor(context, R.color.darkGray))))
    } else {
        ColorFilter.tint(ColorProvider(Color.Black, Color.White))
    }

    var modifier = GlanceModifier.size(48.dp).padding(5.dp)

    if (!disabled) modifier = modifier.clickable(actionProvider())

    Image(ImageProvider(icon), "Toggle Repeat", modifier = modifier, colorFilter = colorFilter)
}

@Composable
fun PlayButton(playerState: PlayerState, playerSize: PlayerSize, disabled: Boolean) {
    if (playerState.isPlaying == true) {
        ActionButton(R.drawable.pause_regular_132, disabled) { actionRunCallback(PauseAction::class.java) }
    } else {
        ActionButton(R.drawable.play_regular_132, disabled) { actionRunCallback(PlayAction::class.java) }
    }
}

@Composable
fun OptionsRows(
    context: Context,
    playerState: PlayerState,
    showToggle: Boolean,
    baseDisabled: Boolean,
    disabledActions: Map<PlayerAction, Boolean>,
    playerSize: PlayerSize
) {
    val pending = playerState.pendingActions

    Row(modifier = GlanceModifier.fillMaxWidth().height(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ActionButton(
            R.drawable.skip_previous_regular_132,
            baseDisabled || pending.contains(PlayerAction.SKIP_PREVIOUS) || pending.contains(PlayerAction.SEEK) ||
                (disabledActions[PlayerAction.SKIP_PREVIOUS] == true && disabledActions[PlayerAction.SEEK] == true)
        ) { actionRunCallback(PreviousAction::class.java) }
        ActionButton(
            R.drawable.rewind_regular_132,
            baseDisabled || pending.contains(PlayerAction.SEEK) || disabledActions[PlayerAction.SEEK] == true
        ) { actionRunCallback(RewindAction::class.java) }
        val playPauseAction = if (playerState.isPlaying == true) PlayerAction.PAUSE else PlayerAction.PLAY
        PlayButton(playerState, playerSize, baseDisabled || pending.contains(playPauseAction))
        ActionButton(
            R.drawable.fast_forward_regular_132,
            baseDisabled || pending.contains(PlayerAction.SEEK) || disabledActions[PlayerAction.SEEK] == true
        ) { actionRunCallback(FastForwardAction::class.java) }
        ActionButton(
            R.drawable.skip_next_regular_132,
            baseDisabled || pending.contains(PlayerAction.SKIP_NEXT) || disabledActions[PlayerAction.SKIP_NEXT] == true
        ) { actionRunCallback(NextAction::class.java) }

        if (showToggle){
            ActionButton(R.drawable.dots_vertical_rounded_regular_132, baseDisabled) { actionRunCallback(ToggleOptionsMenuCallback::class.java)}
        }
    }

    Row(modifier = GlanceModifier.fillMaxWidth().height(50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val repeatIcon = when (playerState.isRepeating) {
            RepeatState.OFF, RepeatState.CONTEXT -> R.drawable.repeat_regular_132
            RepeatState.TRACK -> R.drawable.repeat_regular_1_132
            else -> R.drawable.repeat_regular_132
        }

        val canRepeat = !baseDisabled && !pending.contains(PlayerAction.TOGGLE_REPEAT) && playerState.disabledActions[PlayerAction.TOGGLE_REPEAT] != true
        ActionButton(repeatIcon, !canRepeat, playerState.isRepeating == RepeatState.OFF) { actionRunCallback(ToggleRepeatAction::class.java) }
        val canShuffle = !baseDisabled && !pending.contains(PlayerAction.TOGGLE_SHUFFLE) && playerState.disabledActions[PlayerAction.TOGGLE_SHUFFLE] != true
        ActionButton(R.drawable.shuffle_regular_132, !canShuffle, playerState.isShuffling != true) { actionRunCallback(ToggleShuffleAction::class.java) }
        ActionButton(R.drawable.playlist_solid_132, baseDisabled) {
            val intent = Intent(context, QueueActivity::class.java)
            actionStartActivity(intent)
        }

        if (disabledActions[PlayerAction.SET_VOLUME] != true){
            val canMakeQuieter = playerState.volume?.let { v -> v > 0.0f } ?: false
            ActionButton(R.drawable.volume_low_regular_132, baseDisabled || pending.contains(PlayerAction.SET_VOLUME), !canMakeQuieter) { actionRunCallback(QuieterAction::class.java)}
        }

        if (disabledActions[PlayerAction.SET_VOLUME] != true){
            val canMakeLouder = playerState.volume?.let { v -> v < 1.0f } ?: false
            ActionButton(R.drawable.volume_full_regular_132, baseDisabled || pending.contains(PlayerAction.SET_VOLUME), !canMakeLouder) { actionRunCallback(LouderAction::class.java)}
        }
    }
}

class PlayerDataType(
    val playerViewProvider: PlayerViewProvider,
    val karooSystemServiceProvider: KarooSystemServiceProvider,
    val playerInPreviewModeProvider: PlayerInPreviewModeProvider
) : DataTypeImpl("karoo-tunes", "player") {
    companion object {
        const val DATA_TYPE_ID = "TYPE_EXT::karoo-tunes::player"
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Log.d(KarooTunesExtension.TAG, "Starting player view with $emitter - $config")

        val configJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(UpdateGraphicConfig(showHeader = false))

            if (config.preview){
                playerInPreviewModeProvider.update {
                    it.copy(inPreviewMode = it.inPreviewMode + 1)
                }
            }

            try {
                awaitCancellation()
            } finally {
                if (config.preview) {
                    playerInPreviewModeProvider.update { state ->
                        state.copy(inPreviewMode = state.inPreviewMode - 1)
                    }
                }
            }
        }

        val playerSize = if (config.gridSize.first <= 30){
            PlayerSize.SINGLE_FIELD
        } else if (config.gridSize.second <= 30){
            PlayerSize.SMALL
        } else if (config.gridSize.second < 60){
            PlayerSize.MEDIUM
        } else {
            PlayerSize.FULL_PAGE
        }

        val viewJob = CoroutineScope(Dispatchers.IO).launch {
            emitter.onNext(ShowCustomStreamState("", null))
            if (config.preview) {
                playerViewProvider.provideView(playerSize, config.viewSize).collect { views ->
                    emitter.updateView(views)
                }
            } else {
                karooSystemServiceProvider.karooSystemService.streamDatatypeIsVisible(dataTypeId).collectLatest { playerIsVisible ->
                    if (playerIsVisible) {
                        playerViewProvider.provideView(playerSize, config.viewSize).collect { views ->
                            emitter.updateView(views)
                        }
                    }
                }
            }
        }
        emitter.setCancellable {
            Log.d(KarooTunesExtension.TAG, "Stopping player view with $emitter")

            configJob.cancel()
            viewJob.cancel()
        }
    }
}