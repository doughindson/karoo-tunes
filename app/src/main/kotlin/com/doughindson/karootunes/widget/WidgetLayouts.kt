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
import kotlin.math.floor

private val OUTER_PADDING = 12.dp
private val SECTION_GAP = 10.dp

/** Space for the title and artist lines under the album art. */
private val TRACK_TEXT_HEIGHT = 48.dp

/** Glance sizes everything explicitly: RemoteViews can't measure, so all maths happens here. */
@Composable
fun PlayerWidget(model: WidgetModel, artwork: Bitmap?, size: DpSize) {
    when (model.layout) {
        PlayerLayout.FULL_PAGE -> FullPageLayout(model, artwork, size)
        PlayerLayout.MEDIUM -> MediumLayout(model, artwork, size)
        PlayerLayout.COMPACT -> CompactLayout(model, size)
    }
}

/**
 * Square album art with a column of option buttons beside it, track text, and a transport row
 * filling the rest of the page.
 */
@Composable
private fun FullPageLayout(model: WidgetModel, artwork: Bitmap?, size: DpSize) {
    // rowWidth = art + gap + button, art = n·button + (n-1)·gap (so the art is as tall as the column
    // of n buttons), gap = 0.15·button  ⇒  rowWidth = (n + 1 + 0.15·n)·button. Floor so rounding
    // never overflows the row.
    val rowWidth = (size.width - OUTER_PADDING * 2).value
    val button = floor(rowWidth / (OPTION_COUNT + 1 + 0.15f * OPTION_COUNT)).dp
    val gap = floor(button.value * 0.15f).dp
    val artSize = button * OPTION_COUNT + gap * (OPTION_COUNT - 1)

    Column(modifier = GlanceModifier.fillMaxSize().padding(OUTER_PADDING)) {
        ArtworkWithOptions(model, artwork, artSize, buttonWidth = button, gap = gap)
        Spacer(GlanceModifier.height(12.dp))
        TrackText(model)
        Spacer(GlanceModifier.height(12.dp))

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            transportCommands(model).forEachIndexed { index, command ->
                if (index > 0) Spacer(GlanceModifier.width(SECTION_GAP))
                CommandButton(model, command, GlanceModifier.defaultWeight().fillMaxHeight(), 36.dp)
            }
        }
    }
}

/**
 * Album art with the option buttons beside it and the track underneath. The art takes the
 * height that's left after the text; the buttons share the remaining width.
 */
@Composable
private fun MediumLayout(model: WidgetModel, artwork: Bitmap?, size: DpSize) {
    val rowWidth = size.width - OUTER_PADDING * 2
    val gap = 8.dp
    val heightForArt = size.height - OUTER_PADDING * 2 - SECTION_GAP - TRACK_TEXT_HEIGHT
    // Keep at least ~38% of the width for the buttons so they stay easy to hit.
    val artSize = floor(minOf(heightForArt, rowWidth * 0.62f).value).dp

    Column(modifier = GlanceModifier.fillMaxSize().padding(OUTER_PADDING)) {
        ArtworkWithOptions(model, artwork, artSize, buttonWidth = rowWidth - artSize - gap, gap = gap)
        Spacer(GlanceModifier.height(SECTION_GAP))
        TrackText(model)
    }
}

/** Just the Spotify buttons, filling the field. */
@Composable
private fun CompactLayout(model: WidgetModel, size: DpSize) {
    val width = size.width - OUTER_PADDING * 2
    val height = size.height - OUTER_PADDING * 2
    Box(modifier = GlanceModifier.fillMaxSize().padding(OUTER_PADDING), contentAlignment = Alignment.Center) {
        ButtonRow(OPTION_COUNT, width, height, gap = floor(height.value * 0.12f).dp.coerceAtMost(12.dp)) { index, modifier ->
            OptionButton(model, index, modifier, iconSizeFor(width / OPTION_COUNT, height))
        }
    }
}

/** Square [artSize] artwork, then a column of the option buttons, each [buttonWidth] wide. */
@Composable
private fun ArtworkWithOptions(model: WidgetModel, artwork: Bitmap?, artSize: Dp, buttonWidth: Dp, gap: Dp) {
    val buttonHeight = floor((artSize - gap * (OPTION_COUNT - 1)).value / OPTION_COUNT).dp
    // The last button absorbs rounding so the column is exactly as tall as the art.
    val lastHeight = artSize - gap * (OPTION_COUNT - 1) - buttonHeight * (OPTION_COUNT - 1)
    // Wide, short buttons (medium layout) can take a larger share of their height for the icon.
    val iconSize = iconSizeFor(buttonWidth, buttonHeight, fraction = if (buttonWidth > buttonHeight * 1.5f) 0.75f else 0.55f)

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
                val height = if (index == OPTION_COUNT - 1) lastHeight else buttonHeight
                OptionButton(model, index, GlanceModifier.size(buttonWidth, height), iconSize)
            }
        }
    }
}
