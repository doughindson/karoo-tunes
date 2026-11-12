package com.doughindson.karootunes.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.doughindson.karootunes.data.auth.SpotifyAuth
import com.doughindson.karootunes.ui.home.HomeViewModel
import com.doughindson.karootunes.ui.theme.AppTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The app's only activity: settings/login, library browsing, and the Spotify login redirect
 * (karootunes://oauth2redirect). The widget opens it straight at the library.
 */
class MainActivity : ComponentActivity() {
    private val homeViewModel: HomeViewModel by viewModel()
    private val startAtLibrary = mutableStateOf(false)
    private val launchCount = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        setContent {
            AppTheme {
                // A new launch (e.g. from the widget while the app is open) starts navigation afresh.
                key(launchCount.intValue) {
                    AppNavHost(startAtLibrary = startAtLibrary.value, onExit = ::finish)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
        launchCount.intValue++
    }

    private fun handle(intent: Intent) {
        startAtLibrary.value = intent.getStringExtra(EXTRA_DESTINATION) == DESTINATION_LIBRARY
        val data = intent.data
        if (data != null && data.toString().startsWith(SpotifyAuth.REDIRECT_URI)) {
            homeViewModel.completeLogin(
                code = data.getQueryParameter("code"),
                state = data.getQueryParameter("state"),
                error = data.getQueryParameter("error"),
            )
        }
    }

    companion object {
        private const val EXTRA_DESTINATION = "destination"
        private const val DESTINATION_LIBRARY = "library"

        fun libraryIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_DESTINATION, DESTINATION_LIBRARY)
    }
}
