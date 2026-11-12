package com.doughindson.karootunes.di

import com.doughindson.karootunes.AppScope
import com.doughindson.karootunes.BuildConfig
import com.doughindson.karootunes.data.ThumbnailCache
import com.doughindson.karootunes.data.auth.DataStoreTokenStore
import com.doughindson.karootunes.data.auth.SpotifyAuth
import com.doughindson.karootunes.data.auth.TokenStore
import com.doughindson.karootunes.data.http.HttpTransport
import com.doughindson.karootunes.data.http.KarooHttpTransport
import com.doughindson.karootunes.data.karoo.KarooGateway
import com.doughindson.karootunes.data.settings.SettingsRepository
import com.doughindson.karootunes.data.spotify.ResponseCache
import com.doughindson.karootunes.data.spotify.SpotifyWebApi
import com.doughindson.karootunes.playback.PlaybackController
import com.doughindson.karootunes.playback.PlayerCommands
import com.doughindson.karootunes.playback.PlayerRepository
import com.doughindson.karootunes.playback.PlayerVisibility
import com.doughindson.karootunes.playback.WebPlayback
import com.doughindson.karootunes.playback.WidgetUiStateHolder
import com.doughindson.karootunes.ui.TrackListRoute
import com.doughindson.karootunes.ui.home.HomeViewModel
import com.doughindson.karootunes.ui.library.LibraryViewModel
import com.doughindson.karootunes.ui.tracks.TrackListViewModel
import com.doughindson.karootunes.widget.PlayerDataType
import com.doughindson.karootunes.widget.WidgetRenderer
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModel
import java.io.File

val dataModule = module {
    singleOf(::AppScope)
    singleOf(::KarooGateway)
    singleOf(::SettingsRepository)
    singleOf(::KarooHttpTransport) bind HttpTransport::class
    singleOf(::DataStoreTokenStore) bind TokenStore::class
    single { SpotifyAuth(get(), get(), BuildConfig.SPOTIFY_CLIENT_ID) }
    single { ResponseCache(File(androidContext().cacheDir, "webapi")) }
    single { SpotifyWebApi(get(), get(), get()) }
    singleOf(::ThumbnailCache)
}

val playbackModule = module {
    singleOf(::WebPlayback) bind PlaybackController::class
    singleOf(::PlayerVisibility)
    singleOf(::WidgetUiStateHolder)
    single { PlayerRepository(get(), get(), get(), get(), get(), get(), get()) }
    singleOf(::PlayerCommands)
}

val widgetModule = module {
    singleOf(::WidgetRenderer)
    singleOf(::PlayerDataType)
}

val uiModule = module {
    viewModelOf(::HomeViewModel)
    viewModelOf(::LibraryViewModel)
    viewModel { (route: TrackListRoute) -> TrackListViewModel(route, get(), get()) }
}

val appModules = listOf(dataModule, playbackModule, widgetModule, uiModule)
