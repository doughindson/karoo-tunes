package com.doughindson.karootunes.ui.home

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doughindson.karootunes.R
import com.doughindson.karootunes.playback.PlayerRepository
import com.doughindson.karootunes.playback.PlayerVisibility
import com.doughindson.karootunes.ui.components.BackButton
import com.doughindson.karootunes.widget.PlayerLayout
import com.doughindson.karootunes.widget.WidgetRenderer
import org.koin.compose.koinInject

/** Shows the real widget at the device's screen size. Tap the label to cycle through the layouts. */
@Composable
fun PlayerPreviewDialog(onDismiss: () -> Unit) {
    val renderer = koinInject<WidgetRenderer>()
    val visibility = koinInject<PlayerVisibility>()
    val player = koinInject<PlayerRepository>()
    var layout by remember { mutableStateOf(PlayerLayout.FULL_PAGE) }

    DisposableEffect(Unit) {
        visibility.enterPreview()
        player.start() // no-op when the extension service already started it
        onDispose { visibility.exitPreview() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The preview should match the Karoo's whole screen, not the dialog window.
        val metrics = LocalResources.current.displayMetrics
        val screen = DpSize((metrics.widthPixels / metrics.density).dp, (metrics.heightPixels / metrics.density).dp)
        val fieldSize = DpSize(screen.width, screen.height * PlayerLayout.previewHeightFraction(layout))
        val views: RemoteViews? by remember(layout) { renderer.views(layout, fieldSize) }.collectAsStateWithLifecycle(initialValue = null)

        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AndroidView(
                factory = { context -> FrameLayout(context) },
                update = { frame ->
                    frame.removeAllViews()
                    views?.apply(frame.context, frame)?.let(frame::addView)
                },
                modifier = Modifier.align(Alignment.Center).size(fieldSize),
            )
            FilledTonalButton(
                onClick = { layout = PlayerLayout.entries[(layout.ordinal + 1) % PlayerLayout.entries.size] },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Text(stringResource(R.string.preview_layout, layout.name))
            }
            BackButton(onDismiss)
        }
    }
}
