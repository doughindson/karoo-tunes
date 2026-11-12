package com.doughindson.karootunes.widget

import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.ThumbnailCache
import com.doughindson.karootunes.data.settings.SettingsRepository
import com.doughindson.karootunes.playback.PlayerRepository
import com.doughindson.karootunes.playback.WidgetUiStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Turns player state into RemoteViews for one layout, rendering only when the visible model changes. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class, ExperimentalCoroutinesApi::class)
class WidgetRenderer(
    private val context: Context,
    private val player: PlayerRepository,
    private val widgetUi: WidgetUiStateHolder,
    private val settings: SettingsRepository,
    private val thumbnails: ThumbnailCache,
) {
    private val glance = GlanceRemoteViews()

    fun models(layout: PlayerLayout): Flow<WidgetModel> {
        // Renders only when the state changes; there's deliberately no clock. Every render makes the
        // Karoo rebuild the field on its UI thread (the one handling taps), so the progress bar moves
        // when the player refreshes (every 25 s, after a press, or at the end of a track).
        return combine(player.state, widgetUi.state, settings.settings) { state, ui, settings ->
            WidgetModel.from(state, ui, layout, settings.highResThumbnails, player.now())
        }.distinctUntilChanged()
    }

    fun views(layout: PlayerLayout, size: DpSize): Flow<RemoteViews> {
        // bitmap() answers from memory immediately, so re-subscribing per model change is cheap.
        return models(layout)
            .flatMapLatest { model -> thumbnails.bitmap(model.artworkUrl).map { model to it } }
            .distinctUntilChanged()
            .map { (model, bitmap) ->
                val started = System.nanoTime()
                glance.compose(context, size) { PlayerWidget(model, bitmap, size) }.remoteViews.also {
                    Log.v(TAG, "Rendered ${layout.name} in ${(System.nanoTime() - started) / 1_000_000} ms")
                }
            }
    }
}
