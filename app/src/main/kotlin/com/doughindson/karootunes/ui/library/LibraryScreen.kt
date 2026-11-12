package com.doughindson.karootunes.ui.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.doughindson.karootunes.R
import com.doughindson.karootunes.ui.components.BackButton
import com.doughindson.karootunes.ui.components.ErrorMessage
import com.doughindson.karootunes.ui.components.Thumbnail
import com.doughindson.karootunes.ui.tracks.TrackListKind
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenList: (kind: TrackListKind, id: String?, title: String?) -> Unit,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val pager = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // Spotify's terms ask apps showing Spotify content to attribute it.
            Image(
                painterResource(R.drawable.spotify_full_logo_rgb_black),
                contentDescription = stringResource(R.string.spotify_logo),
                modifier = Modifier.fillMaxWidth().height(48.dp).padding(8.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            )
            PrimaryTabRow(selectedTabIndex = pager.currentPage) {
                Tab(
                    selected = pager.currentPage == 0,
                    onClick = { scope.launch { pager.animateScrollToPage(0) } },
                    text = { Text(stringResource(R.string.tab_playlists)) },
                    icon = { Icon(painterResource(R.drawable.ic_queue_music), contentDescription = null) },
                )
                Tab(
                    selected = pager.currentPage == 1,
                    onClick = { scope.launch { pager.animateScrollToPage(1) } },
                    text = { Text(stringResource(R.string.tab_podcasts)) },
                    icon = { Icon(painterResource(R.drawable.ic_podcast), contentDescription = null) },
                )
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> CollectionList(
                        viewModel = viewModel,
                        kind = CollectionKind.PLAYLISTS,
                        shortcuts = listOf(
                            Shortcut(R.drawable.ic_queue_music, R.string.queue) { onOpenList(TrackListKind.QUEUE, null, null) },
                            Shortcut(R.drawable.ic_favorite, R.string.saved_songs) { onOpenList(TrackListKind.SAVED_TRACKS, null, null) },
                        ),
                        onOpen = { item -> onOpenList(TrackListKind.PLAYLIST, item.id, item.name) },
                    )
                    else -> CollectionList(
                        viewModel = viewModel,
                        kind = CollectionKind.SHOWS,
                        shortcuts = listOf(
                            Shortcut(R.drawable.ic_podcast, R.string.saved_episodes) { onOpenList(TrackListKind.SAVED_EPISODES, null, null) },
                        ),
                        onOpen = { item -> onOpenList(TrackListKind.SHOW, item.id, item.name) },
                    )
                }
            }
        }
        BackButton(onBack)
    }
}

private data class Shortcut(@DrawableRes val icon: Int, val label: Int, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionList(
    viewModel: LibraryViewModel,
    kind: CollectionKind,
    shortcuts: List<Shortcut>,
    onOpen: (CollectionItem) -> Unit,
) {
    val items = (if (kind == CollectionKind.PLAYLISTS) viewModel.playlists else viewModel.shows).collectAsLazyPagingItems()
    val refreshing = items.loadState.refresh is LoadState.Loading

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            viewModel.clearCache(kind)
            items.refresh()
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            shortcuts.forEach { shortcut ->
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable(onClick = shortcut.onClick).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(shortcut.icon), contentDescription = null, modifier = Modifier.size(48.dp).padding(8.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(shortcut.label), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            (items.loadState.refresh as? LoadState.Error)?.let { error ->
                item { ErrorMessage(stringResource(R.string.list_error, error.error.message.orEmpty()), onRetry = items::retry) }
            }
            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                val item = items[index] ?: return@items
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumbnail(item.imageUrl)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        item.itemCount?.let { count ->
                            Text(
                                pluralStringResource(if (kind == CollectionKind.PLAYLISTS) R.plurals.track_count else R.plurals.episode_count, count, count),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}
