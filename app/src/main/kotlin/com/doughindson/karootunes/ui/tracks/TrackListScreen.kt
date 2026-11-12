package com.doughindson.karootunes.ui.tracks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.doughindson.karootunes.R
import com.doughindson.karootunes.ui.TrackListRoute
import com.doughindson.karootunes.ui.components.BackButton
import com.doughindson.karootunes.ui.components.ErrorMessage
import com.doughindson.karootunes.ui.components.Thumbnail
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

private const val MAX_SELECTION = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackListScreen(
    route: TrackListRoute,
    onBack: () -> Unit,
    onPlaybackStarted: () -> Unit,
    viewModel: TrackListViewModel = koinViewModel(key = route.toString()) { parametersOf(route) },
) {
    val resources = LocalResources.current
    val items = viewModel.items.collectAsLazyPagingItems()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val selected = remember { mutableStateListOf<TrackItem>() }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TrackListEvent.PlaybackStarted -> onPlaybackStarted()
                is TrackListEvent.Failed -> snackbar.showSnackbar(resources.getString(R.string.action_failed, event.message))
            }
        }
    }

    val title = viewModel.title ?: stringResource(
        when (viewModel.kind) {
            TrackListKind.PLAYLIST -> R.string.playlist
            TrackListKind.SHOW -> R.string.show
            TrackListKind.SAVED_TRACKS -> R.string.saved_songs
            TrackListKind.SAVED_EPISODES -> R.string.saved_episodes
            TrackListKind.QUEUE -> R.string.queue
        }
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (viewModel.contextUri != null && selected.isEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { if (!busy) viewModel.playAll() },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text(stringResource(R.string.play_all)) },
                )
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                BottomAppBar {
                    IconButton(enabled = !busy, onClick = { viewModel.playSelection(selected.toList()) }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_play))
                    }
                    IconButton(enabled = !busy, onClick = { viewModel.addToQueue(selected.toList()) }) {
                        Icon(painterResource(R.drawable.ic_playlist_add), contentDescription = stringResource(R.string.add_to_queue))
                    }
                    IconButton(enabled = !busy, onClick = { viewModel.like(selected.toList()); selected.clear() }) {
                        Icon(painterResource(R.drawable.ic_favorite_border), contentDescription = stringResource(R.string.add_to_liked))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = items.loadState.refresh is LoadState.Loading,
                onRefresh = {
                    viewModel.clearCache()
                    items.refresh()
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                TrackList(
                    items = items,
                    selected = selected,
                    onTap = { item ->
                        when {
                            selected.isNotEmpty() -> selected.toggle(item)
                            !busy -> viewModel.playFrom(item, items.itemSnapshotList.items)
                        }
                    },
                    onLongPress = { item -> selected.toggle(item) },
                )
            }
            if (selected.isEmpty()) BackButton(onBack)
        }
    }
}

private fun MutableList<TrackItem>.toggle(item: TrackItem) {
    if (!remove(item)) {
        if (size >= MAX_SELECTION) removeAt(0)
        add(item)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackList(
    items: LazyPagingItems<TrackItem>,
    selected: List<TrackItem>,
    onTap: (TrackItem) -> Unit,
    onLongPress: (TrackItem) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        val refresh = items.loadState.refresh
        if (refresh is LoadState.Error) {
            item {
                val message = if (refresh.error.isPlaylistAccessDenied()) {
                    stringResource(R.string.playlist_items_unavailable)
                } else {
                    stringResource(R.string.list_error, refresh.error.message.orEmpty())
                }
                ErrorMessage(message, onRetry = items::retry)
            }
        } else if (refresh is LoadState.NotLoading && items.itemCount == 0) {
            item { Text(stringResource(R.string.empty_list), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge) }
        }

        // Keys by position: the same track can appear twice in a playlist or queue.
        items(count = items.itemCount) { index ->
            val item = items[index] ?: return@items
            TrackRow(
                item = item,
                isSelected = item in selected,
                selectionMode = selected.isNotEmpty(),
                modifier = Modifier.combinedClickable(onLongClick = { onLongPress(item) }, onClick = { onTap(item) }),
            )
        }
        (items.loadState.append as? LoadState.Error)?.let { error ->
            item { ErrorMessage(stringResource(R.string.list_error, error.error.message.orEmpty()), onRetry = items::retry) }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun TrackRow(item: TrackItem, isSelected: Boolean, selectionMode: Boolean, modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(item.imageUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                style = if (item.isEpisode) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                maxLines = if (item.isEpisode) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                Text(
                    item.subtitle.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                item.durationMs?.let {
                    Text(formatDuration(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (selectionMode) Checkbox(checked = isSelected, onCheckedChange = null)
    }
}

internal fun formatDuration(ms: Int): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}
