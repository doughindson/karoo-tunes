package com.doughindson.karootunes.ui.tracks

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
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
import com.doughindson.karootunes.ui.components.BottomClearance
import com.doughindson.karootunes.ui.components.ErrorMessage
import com.doughindson.karootunes.ui.components.InfoMessage
import com.doughindson.karootunes.ui.components.ListRow
import com.doughindson.karootunes.ui.components.PillButton
import com.doughindson.karootunes.ui.components.PlayButton
import com.doughindson.karootunes.ui.components.ScreenHeader
import com.doughindson.karootunes.ui.components.SpotifyIcon
import com.doughindson.karootunes.ui.components.Thumbnail
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

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
    val snackbar = remember { SnackbarHostState() }
    var actionsFor by remember { mutableStateOf<TrackItem?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                TrackListEvent.PlaybackStarted -> onPlaybackStarted()
                is TrackListEvent.Done -> {
                    // A song removed from Liked Songs should disappear from this list.
                    if (event.message == R.string.removed_from_liked) items.refresh()
                    snackbar.showSnackbar(resources.getString(event.message))
                }
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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title, maxLines = 2) {
                Spacer(Modifier.width(8.dp))
                SpotifyIcon(22.dp)
                if (viewModel.canPlayAll) {
                    Spacer(Modifier.width(12.dp))
                    PlayButton(onClick = { viewModel.playAll(items.itemSnapshotList.items) }, enabled = !busy)
                }
            }
            TrackList(
                items = items,
                onRefresh = {
                    viewModel.clearCache()
                    items.refresh()
                },
                onTap = { item -> if (!busy) viewModel.playFrom(item, items.itemSnapshotList.items) },
                onLongPress = { item -> actionsFor = item },
                modifier = Modifier.weight(1f),
            )
        }
        BackButton(onBack)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 76.dp))
    }

    actionsFor?.let { item ->
        ModalBottomSheet(
            onDismissRequest = { actionsFor = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            TrackActions(
                item = item,
                busy = busy,
                likeRemoves = viewModel.likeActionRemoves,
                onAddToQueue = {
                    viewModel.addToQueue(item)
                    actionsFor = null
                },
                onLike = {
                    viewModel.toggleLike(item)
                    actionsFor = null
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackList(
    items: LazyPagingItems<TrackItem>,
    onRefresh: () -> Unit,
    onTap: (TrackItem) -> Unit,
    onLongPress: (TrackItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val refreshing = items.loadState.refresh is LoadState.Loading
    val refreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = refreshing,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            when (val refresh = items.loadState.refresh) {
                is LoadState.Error -> item {
                    if (refresh.error.isPlaylistAccessDenied()) {
                        // Not a failure: Spotify only lists playlists you own or collaborate on.
                        InfoMessage(stringResource(R.string.playlist_items_unavailable))
                    } else {
                        ErrorMessage(stringResource(R.string.list_error, refresh.error.message.orEmpty()), onRetry = items::retry)
                    }
                }
                is LoadState.NotLoading -> if (items.itemCount == 0) item { InfoMessage(stringResource(R.string.empty_list)) }
                else -> Unit
            }

            // Keyed by position: the same track can appear twice in a playlist or queue.
            items(count = items.itemCount) { index ->
                val item = items[index] ?: return@items
                ListRow(
                    title = item.name,
                    subtitle = item.subtitle,
                    onClick = { onTap(item) },
                    onLongClick = { onLongPress(item) },
                    titleLines = if (item.isEpisode) 2 else 1,
                ) {
                    Thumbnail(item.imageUrl)
                }
            }
            (items.loadState.append as? LoadState.Error)?.let { error ->
                item { ErrorMessage(stringResource(R.string.list_error, error.error.message.orEmpty()), onRetry = items::retry) }
            }
            item { Spacer(Modifier.height(BottomClearance)) }
        }
    }
}

/** The long-press sheet: the track, then large actions that are easy to hit on the Karoo. */
@Composable
private fun TrackActions(item: TrackItem, busy: Boolean, likeRemoves: Boolean, onAddToQueue: () -> Unit, onLike: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(item.imageUrl, size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        PillButton(stringResource(R.string.add_to_queue), onAddToQueue, enabled = !busy, icon = R.drawable.ic_playlist_add)
        PillButton(
            stringResource(if (likeRemoves) R.string.remove_from_liked else R.string.add_to_liked),
            onLike,
            enabled = !busy,
            icon = if (likeRemoves) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
        )
    }
}
