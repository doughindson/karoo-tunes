package com.doughindson.karootunes.widget

import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import com.doughindson.karootunes.R
import com.doughindson.karootunes.playback.PlayerCommand
import com.doughindson.karootunes.ui.MainActivity

/** Day/night pairs; keep in sync with res/values(-night)/colors.xml, which the button shapes use. */
internal object WidgetColors {
    val textPrimary = ColorProvider(day = Color(0xFF000000), night = Color(0xFFFFFFFF))
    val textSecondary = ColorProvider(day = Color(0xFF555555), night = Color(0xFFAAAAAA))
    val icon = ColorProvider(day = Color(0xFF333333), night = Color(0xFFC8C8C8))
    val iconDisabled = ColorProvider(day = Color(0xFFB8B8B8), night = Color(0xFF5A5A5A))
    // Day uses a darker green: Spotify green on a light-grey button is too faint.
    val iconActive = ColorProvider(day = Color(0xFF15883E), night = Color(0xFF1ED760))
    val onAccent = ColorProvider(day = Color(0xFF000000), night = Color(0xFF000000))
}

internal fun commandAction(command: PlayerCommand): Action =
    actionRunCallback<PlayerActionCallback>(actionParametersOf(PlayerActionCallback.COMMAND to command.name))

@Composable
internal fun openLibraryAction(): Action {
    val context = LocalContext.current
    return actionStartActivity(MainActivity.libraryIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
internal fun text(value: WidgetText): String = when (value) {
    is WidgetText.Literal -> value.text
    is WidgetText.Resource -> LocalContext.current.getString(value.id)
}

/** A rounded button with a centred icon. Disabled buttons are greyed out and not clickable. */
@Composable
internal fun WidgetButton(
    @DrawableRes icon: Int,
    @StringRes description: Int,
    modifier: GlanceModifier,
    iconSize: Dp,
    onClick: Action,
    enabled: Boolean = true,
    accent: Boolean = false,
    active: Boolean = false,
) {
    val tint = when {
        !enabled -> WidgetColors.iconDisabled
        accent -> WidgetColors.onAccent
        active -> WidgetColors.iconActive
        else -> WidgetColors.icon
    }
    Box(
        modifier = modifier
            // A disabled play button turns grey too, so it doesn't look like the main action.
            .background(ImageProvider(if (accent && enabled) R.drawable.widget_button_accent else R.drawable.widget_button_surface))
            .let { if (enabled) it.clickable(onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            ImageProvider(icon),
            LocalContext.current.getString(description),
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(tint),
        )
    }
}

@Composable
internal fun CommandButton(model: WidgetModel, command: PlayerCommand, modifier: GlanceModifier, iconSize: Dp) {
    val (icon, description) = when (command) {
        PlayerCommand.PLAY -> R.drawable.ic_play_arrow to R.string.action_play
        PlayerCommand.PAUSE -> R.drawable.ic_pause to R.string.action_pause
        PlayerCommand.NEXT -> R.drawable.ic_skip_next to R.string.action_next
        PlayerCommand.PREVIOUS -> R.drawable.ic_skip_previous to R.string.action_previous
        PlayerCommand.SEEK_BACK -> R.drawable.ic_fast_rewind to R.string.action_seek_back
        PlayerCommand.SEEK_FORWARD -> R.drawable.ic_fast_forward to R.string.action_seek_forward
        PlayerCommand.TOGGLE_SHUFFLE -> R.drawable.ic_shuffle to R.string.action_shuffle
        PlayerCommand.TOGGLE_LIKE -> (if (model.liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border) to
            (if (model.liked) R.string.action_unlike else R.string.action_like)
    }
    val active = when (command) {
        PlayerCommand.TOGGLE_SHUFFLE -> model.shuffle
        PlayerCommand.TOGGLE_LIKE -> model.liked
        else -> false
    }
    WidgetButton(
        icon = icon,
        description = description,
        modifier = modifier,
        iconSize = iconSize,
        onClick = commandAction(command),
        enabled = model.isEnabled(command),
        accent = command == PlayerCommand.PLAY || command == PlayerCommand.PAUSE,
        active = active,
    )
}

@Composable
internal fun LibraryButton(modifier: GlanceModifier, iconSize: Dp) {
    WidgetButton(R.drawable.ic_library_music, R.string.action_library, modifier, iconSize, openLibraryAction())
}

/** The three main transport commands. Podcasts get seek buttons instead of skip. */
internal fun transportCommands(model: WidgetModel): List<PlayerCommand> = listOf(
    if (model.isEpisode) PlayerCommand.SEEK_BACK else PlayerCommand.PREVIOUS,
    if (model.isPlaying) PlayerCommand.PAUSE else PlayerCommand.PLAY,
    if (model.isEpisode) PlayerCommand.SEEK_FORWARD else PlayerCommand.NEXT,
)

/** Like / Shuffle / Library: what only Karoo Tunes can do (the Karoo has its own play/pause/skip). */
@Composable
internal fun OptionButton(model: WidgetModel, index: Int, modifier: GlanceModifier, iconSize: Dp) {
    when (index) {
        0 -> CommandButton(model, PlayerCommand.TOGGLE_LIKE, modifier, iconSize)
        1 -> CommandButton(model, PlayerCommand.TOGGLE_SHUFFLE, modifier, iconSize)
        else -> LibraryButton(modifier, iconSize)
    }
}

internal const val OPTION_COUNT = 3

/** Lays out [count] equal-width buttons with gaps, absorbing rounding in the last one so nothing clips. */
@Composable
internal fun ButtonRow(
    count: Int,
    width: Dp,
    height: Dp,
    gap: Dp,
    content: @Composable (index: Int, modifier: GlanceModifier) -> Unit,
) {
    val buttonWidth = kotlin.math.floor((width.value - gap.value * (count - 1)) / count).dp
    val lastWidth = width - gap * (count - 1) - buttonWidth * (count - 1)
    Row(modifier = GlanceModifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            if (index > 0) Spacer(GlanceModifier.width(gap))
            content(index, GlanceModifier.size(if (index == count - 1) lastWidth else buttonWidth, height))
        }
    }
}

@Composable
internal fun TrackText(model: WidgetModel, modifier: GlanceModifier = GlanceModifier, showSubtitle: Boolean = true) {
    Column(modifier = modifier) {
        Text(
            text(model.title),
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (model.titleMuted) WidgetColors.textSecondary else WidgetColors.textPrimary,
            ),
            maxLines = 1,
        )
        if (showSubtitle) {
            Text(
                model.subtitle?.let { text(it) } ?: "",
                style = TextStyle(fontSize = 15.sp, color = WidgetColors.textSecondary),
                maxLines = model.subtitleMaxLines,
            )
        }
    }
}


internal fun iconSizeFor(buttonWidth: Dp, buttonHeight: Dp, fraction: Float = 0.55f): Dp =
    kotlin.math.min(36f, kotlin.math.min(buttonWidth.value, buttonHeight.value) * fraction).dp
