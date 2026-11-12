package com.doughindson.karootunes.widget

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
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
import com.doughindson.karootunes.R
import com.doughindson.karootunes.playback.PlayerCommand
import kotlin.math.floor

private val OUTER_PADDING = 12.dp
private val SECTION_GAP = 10.dp

/** Glance sizes everything explicitly: RemoteViews can't measure, so all maths happens here. */
@Composable
fun PlayerWidget(model: WidgetModel, artwork: Bitmap?, size: DpSize) {
    when (model.layout) {
        PlayerLayout.FULL_PAGE -> FullPageLayout(model, artwork, size)
        PlayerLayout.MEDIUM -> MediumLayout(model, size)
        PlayerLayout.SMALL -> SmallLayout(model, size)
        PlayerLayout.SINGLE_FIELD -> SingleFieldLayout(model, size)
    }
}

/**
 * Square album art with a column of option buttons beside it, track text, progress bar, and a
 * transport row filling the rest of the page.
 */
@Composable
private fun FullPageLayout(model: WidgetModel, artwork: Bitmap?, size: DpSize) {
    // rowWidth = art + gap + button, art = 4·button + 3·gap (so the art is as tall as the column),
    // gap = 0.15·button  ⇒  rowWidth = 5.6·button. Floor so rounding never overflows the row.
    val rowWidth = (size.width - OUTER_PADDING * 2).value
    val button = floor(rowWidth / 5.6f).dp
    val gap = floor(button.value * 0.15f).dp
    val artSize = button * OPTION_COUNT + gap * (OPTION_COUNT - 1)
    val iconSize = iconSizeFor(button, button)

    Column(modifier = GlanceModifier.fillMaxSize().padding(OUTER_PADDING)) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(artSize)) {
            Box(
                modifier = GlanceModifier.size(artSize)
                    .background(ImageProvider(R.drawable.widget_artwork_placeholder))
                    .cornerRadius(8.dp),
            ) {
                // An empty placeholder reads as "loading" the moment the track changes.
                if (artwork != null) {
                    Image(
                        ImageProvider(artwork),
                        LocalContext.current.getString(R.string.album_art),
                        modifier = GlanceModifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(GlanceModifier.width(gap))
            Column {
                repeat(OPTION_COUNT) { index ->
                    if (index > 0) Spacer(GlanceModifier.height(gap))
                    OptionButton(model, index, GlanceModifier.size(button), iconSize)
                }
            }
        }

        Spacer(GlanceModifier.height(12.dp))
        TrackText(model)
        Spacer(GlanceModifier.height(SECTION_GAP))
        ProgressBar(model)
        Spacer(GlanceModifier.height(12.dp))

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            transportCommands(model).forEachIndexed { index, command ->
                if (index > 0) Spacer(GlanceModifier.width(SECTION_GAP))
                CommandButton(model, command, GlanceModifier.defaultWeight().fillMaxHeight(), 36.dp)
            }
        }
    }
}

/** Track text (or the options row) on top, progress bar, then a large transport row. */
@Composable
private fun MediumLayout(model: WidgetModel, size: DpSize) {
    val contentWidth = size.width - OUTER_PADDING * 2
    val progressHeight = 8.dp
    val remaining = size.height - OUTER_PADDING * 2 - SECTION_GAP * 2 - progressHeight
    // Top row : transport row = 5 : 7.
    val topHeight = floor(remaining.value * 5f / 12f).dp
    val bottomHeight = remaining - topHeight

    Column(modifier = GlanceModifier.fillMaxSize().padding(OUTER_PADDING)) {
        if (model.optionsOpen) {
            OptionsRow(model, contentWidth, topHeight)
        } else {
            Row(modifier = GlanceModifier.fillMaxWidth().height(topHeight), verticalAlignment = Alignment.CenterVertically) {
                TrackText(model, GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(floor(topHeight.value * 0.15f).dp))
                CommandButton(model, PlayerCommand.TOGGLE_OPTIONS, GlanceModifier.size(topHeight), iconSizeFor(topHeight, topHeight))
            }
        }
        Spacer(GlanceModifier.height(SECTION_GAP))
        ProgressBar(model, progressHeight)
        Spacer(GlanceModifier.height(SECTION_GAP))
        TransportRow(model, contentWidth, bottomHeight)
    }
}

/** One line of track text above the compact button row. */
@Composable
private fun SmallLayout(model: WidgetModel, size: DpSize) {
    val textHeight = 28.dp
    val contentWidth = size.width - OUTER_PADDING * 2
    val rowHeight = size.height - OUTER_PADDING * 2 - SECTION_GAP - textHeight
    Column(modifier = GlanceModifier.fillMaxSize().padding(OUTER_PADDING)) {
        TrackText(model, GlanceModifier.fillMaxWidth().height(textHeight), showSubtitle = false)
        Spacer(GlanceModifier.height(SECTION_GAP))
        CompactRow(model, contentWidth, rowHeight)
    }
}

/** Just the compact button row, filling the field. */
@Composable
private fun SingleFieldLayout(model: WidgetModel, size: DpSize) {
    Box(modifier = GlanceModifier.fillMaxSize().padding(OUTER_PADDING), contentAlignment = Alignment.Center) {
        CompactRow(model, size.width - OUTER_PADDING * 2, size.height - OUTER_PADDING * 2)
    }
}

/** Transport buttons plus ⋮, or the options row while it's open. */
@Composable
private fun CompactRow(model: WidgetModel, width: Dp, height: Dp) {
    if (model.optionsOpen) {
        OptionsRow(model, width, height)
    } else {
        val commands = transportCommands(model) + PlayerCommand.TOGGLE_OPTIONS
        ButtonRow(commands.size, width, height, gap = floor(height.value * 0.12f).dp.coerceAtMost(12.dp)) { index, modifier ->
            CommandButton(model, commands[index], modifier, iconSizeFor(width / commands.size, height))
        }
    }
}

@Composable
private fun OptionsRow(model: WidgetModel, width: Dp, height: Dp) {
    ButtonRow(OPTION_COUNT, width, height, gap = floor(height.value * 0.12f).dp.coerceAtMost(12.dp)) { index, modifier ->
        OptionButton(model, index, modifier, iconSizeFor(width / OPTION_COUNT, height))
    }
}

/** Previous / play-pause / next with a wider centre button. */
@Composable
private fun TransportRow(model: WidgetModel, width: Dp, height: Dp) {
    val gap = floor(height.value * 0.15f).dp.coerceAtMost(16.dp)
    val usable = width - gap * 2
    val side = floor(usable.value * 0.325f).dp
    val centre = usable - side * 2
    val iconSize = iconSizeFor(side, height, fraction = 0.4f)
    val commands = transportCommands(model)
    Row(modifier = GlanceModifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        CommandButton(model, commands[0], GlanceModifier.size(side, height), iconSize)
        Spacer(GlanceModifier.width(gap))
        CommandButton(model, commands[1], GlanceModifier.size(centre, height), iconSize)
        Spacer(GlanceModifier.width(gap))
        CommandButton(model, commands[2], GlanceModifier.size(side, height), iconSize)
    }
}

