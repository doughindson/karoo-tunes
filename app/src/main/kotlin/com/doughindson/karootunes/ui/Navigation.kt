package com.doughindson.karootunes.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.doughindson.karootunes.ui.home.HomeScreen
import com.doughindson.karootunes.ui.library.LibraryScreen
import com.doughindson.karootunes.ui.tracks.TrackListKind
import com.doughindson.karootunes.ui.tracks.TrackListScreen
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object LibraryRoute

/** [kind] is a [TrackListKind] name; [id] is the playlist or show ID where relevant. */
@Serializable
data class TrackListRoute(val kind: String, val id: String? = null, val title: String? = null)

@Composable
fun AppNavHost(
    startAtLibrary: Boolean,
    onExit: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    // Back from the start destination leaves the app (e.g. back to the ride when opened from the widget).
    fun back() {
        if (navController.previousBackStackEntry != null) navController.popBackStack() else onExit()
    }

    NavHost(navController, startDestination = if (startAtLibrary) LibraryRoute else HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenLibrary = { navController.navigate(LibraryRoute) },
                onBack = ::back,
            )
        }
        composable<LibraryRoute> {
            LibraryScreen(
                onOpenList = { kind, id, title -> navController.navigate(TrackListRoute(kind.name, id, title)) },
                onBack = ::back,
            )
        }
        composable<TrackListRoute> { entry ->
            TrackListScreen(
                route = entry.toRoute<TrackListRoute>(),
                onBack = ::back,
                // Playback started: return to the ride, as there's nothing more to do here.
                onPlaybackStarted = onExit,
            )
        }
    }
}

fun trackListRoute(kind: TrackListKind, id: String? = null, title: String? = null) = TrackListRoute(kind.name, id, title)
