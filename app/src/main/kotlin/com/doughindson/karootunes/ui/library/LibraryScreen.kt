package com.doughindson.karootunes.ui.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.doughindson.karootunes.R
import com.doughindson.karootunes.ui.components.BackButton
import com.doughindson.karootunes.ui.components.BottomClearance
import com.doughindson.karootunes.ui.components.ErrorMessage
import com.doughindson.karootunes.ui.components.FilterChips
import com.doughindson.karootunes.ui.components.IconTile
import com.doughindson.karootunes.ui.components.ListRow
import com.doughindson.karootunes.ui.components.Thumbnail
import com.doughindson.karootunes.ui.theme.LikedSongsGradient
import com.doughindson.karootunes.ui.theme.SavedEpisodesColor
import com.doughindson.karootunes.ui.tracks.TrackListKind
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

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
            // Spotify's brand rules ask apps showing Spotify content to attribute it with the logo.
            Image(
                painterResource(R.drawable.spotify_full_logo_rgb_black),
                contentDescription = stringResource(R.string.spotify_logo),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                modifier = Modifier
                    .padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
                    .height(28.dp),
            )
            FilterChips(
                options = listOf(stringResource(R.string.tab_playlists), stringResource(R.string.tab_podcasts)),
                selected = pager.currentPage,
                onSelect = { page -> scope.launch { pager.animateScrollToPage(page) } },
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> CollectionList(
                        viewModel = viewModel,
                        kind = CollectionKind.PLAYLISTS,
                        shortcuts = listOf(
                            Shortcut(R.string.saved_songs, R.string.saved_songs_subtitle, R.drawable.ic_favorite, Brush.linearGradient(LikedSongsGradient), Color.White) {
                                onOpenList(TrackListKind.SAVED_TRACKS, null, null)
                            },
                            Shortcut(R.string.queue, R.string.queue_subtitle, R.drawable.ic_queue_music, null, null) {
                                onOpenList(TrackListKind.QUEUE, null, null)
                            },
                        ),
                        onOpen = { item -> onOpenList(TrackListKind.PLAYLIST, item.id, item.name) },
                    )
                    else -> CollectionList(
                        viewModel = viewModel,
                        kind = CollectionKind.SHOWS,
                        shortcuts = listOf(
                            Shortcut(R.string.saved_episodes, R.string.saved_episodes_subtitle, R.drawable.ic_podcast, Brush.linearGradient(listOf(SavedEpisodesColor, SavedEpisodesColor)), Color.White) {
                                onOpenList(TrackListKind.SAVED_EPISODES, null, null)
                            },
                        ),
                        onOpen = { item -> onOpenList(TrackListKind.SHOW, item.id, item.name) },
                    )
                }
            }
        }
        BackButton(onBack)
    }
}

/** A fixed entry at the top of a list. A null [background] uses the neutral tile colour. */
private class Shortcut(
    val title: Int,
    val subtitle: Int,
    val icon: Int,
    val background: Brush?,
    val iconTint: Color?,
    val onClick: () -> Unit,
)

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
    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            viewModel.clearCache(kind)
            items.refresh()
        },
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
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
            shortcuts.forEach { shortcut ->
                item {
                    ListRow(
                        title = stringResource(shortcut.title),
                        subtitle = stringResource(shortcut.subtitle),
                        onClick = shortcut.onClick,
                    ) {
                        IconTile(
                            icon = shortcut.icon,
                            background = shortcut.background ?: Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
                            ),
                            iconTint = shortcut.iconTint ?: MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            (items.loadState.refresh as? LoadState.Error)?.let { error ->
                item { ErrorMessage(stringResource(R.string.list_error, error.error.message.orEmpty()), onRetry = items::retry) }
            }
            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                val item = items[index] ?: return@items
                ListRow(
                    title = item.name,
                    subtitle = item.itemCount?.let { count ->
                        pluralStringResource(
                            if (kind == CollectionKind.PLAYLISTS) R.plurals.playlist_subtitle else R.plurals.show_subtitle,
                            count,
                            count,
                        )
                    } ?: stringResource(if (kind == CollectionKind.PLAYLISTS) R.string.playlist else R.string.show),
                    onClick = { onOpen(item) },
                ) {
                    Thumbnail(item.imageUrl)
                }
            }
            item { Spacer(Modifier.height(BottomClearance)) }
        }
    }
}
