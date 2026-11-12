package com.doughindson.karootunes.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.doughindson.karootunes.R
import com.doughindson.karootunes.data.ThumbnailCache
import org.koin.compose.koinInject

/** Karoo apps put an on-screen back button in the bottom-left corner. */
@Composable
fun BoxScope.BackButton(onBack: () -> Unit) {
    FilledTonalIconButton(
        onClick = onBack,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 8.dp, bottom = 10.dp)
            .size(56.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
}

/** A square thumbnail loaded through [ThumbnailCache]; a neutral placeholder until it arrives. */
@Composable
fun Thumbnail(url: String?, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    val cache = koinInject<ThumbnailCache>()
    val bitmap: ImageBitmap? by produceState(initialValue = url?.let(cache::cached)?.asImageBitmap(), url) {
        if (value == null && url != null) value = cache.get(url)?.asImageBitmap()
    }
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
    ) {
        bitmap?.let {
            // Decorative: the row's text already names the item.
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        }
    }
}

@Composable
fun ErrorMessage(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}
