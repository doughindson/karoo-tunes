package com.doughindson.karootunes.screens

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import com.doughindson.karootunes.theme.AppTheme
import io.hammerhead.karooext.KarooSystemService
import org.koin.compose.KoinContext

class QueueActivity : ComponentActivity() {
    val karooSystemService = KarooSystemService(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        karooSystemService.connect()

        setContent {
            AppTheme {
                KoinContext {
                    PlaylistScreen(
                        navController = null,
                        playlistMode = PlaylistScreenMode.Queue,
                        ::finish
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        karooSystemService.disconnect()
    }
}