package com.doughindson.karootunes.widget

import android.content.Context
import android.util.Log
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.doughindson.karootunes.AppScope
import com.doughindson.karootunes.TAG
import com.doughindson.karootunes.data.karoo.KarooGateway
import com.doughindson.karootunes.playback.PlayerVisibility
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The player data field. Renders only while on screen (or previewed in the profile editor). */
class PlayerDataType(
    private val renderer: WidgetRenderer,
    private val karoo: KarooGateway,
    private val visibility: PlayerVisibility,
    private val appScope: AppScope,
) : DataTypeImpl(EXTENSION_ID, TYPE_ID) {

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val density = context.resources.displayMetrics.density
        val size = DpSize((config.viewSize.first / density).dp, (config.viewSize.second / density).dp)
        val layout = PlayerLayout.forField(config.gridSize)
        Log.d(TAG, "Starting player view: $layout, grid ${config.gridSize}, $size, preview=${config.preview}")

        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        emitter.onNext(ShowCustomStreamState("", null))
        if (config.preview) visibility.enterPreview()

        val job = appScope.launch {
            if (config.preview) {
                renderer.views(layout, size).collect(emitter::updateView)
            } else {
                karoo.isDataTypeOnScreen(DATA_TYPE_ID).collectLatest { onScreen ->
                    if (onScreen) renderer.views(layout, size).collect(emitter::updateView)
                }
            }
        }

        emitter.setCancellable {
            Log.d(TAG, "Stopping player view ($layout)")
            job.cancel()
            if (config.preview) visibility.exitPreview()
        }
    }

    companion object {
        const val EXTENSION_ID = "karoo-tunes"
        const val TYPE_ID = "player"
        const val DATA_TYPE_ID = "TYPE_EXT::$EXTENSION_ID::$TYPE_ID"
    }
}
