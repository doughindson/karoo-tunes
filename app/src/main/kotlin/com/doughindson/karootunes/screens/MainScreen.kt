package com.doughindson.karootunes.screens

import android.util.Log
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doughindson.karootunes.KarooTunesExtension
import com.doughindson.karootunes.KarooTunesServices
import com.doughindson.karootunes.KarooSystemServiceProvider
import com.doughindson.karootunes.R
import com.doughindson.karootunes.auth.OAuth2Client
import com.doughindson.karootunes.auth.TokenResponse
import com.doughindson.karootunes.datatypes.PlayerSize
import com.doughindson.karootunes.datatypes.PlayerViewProvider
import com.doughindson.karootunes.spotify.LocalClient
import com.doughindson.karootunes.spotify.LocalClientConnectionState
import com.doughindson.karootunes.spotify.PlayerInPreviewModeProvider
import io.hammerhead.karooext.models.HardwareType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onFinish: () -> Unit) {
    val localClient = koinInject<LocalClient>()
    val karooSystemServiceProvider = koinInject<KarooSystemServiceProvider>()
    val oauth2Client = koinInject<OAuth2Client>()
    val viewProvider = koinInject<PlayerViewProvider>()
    val playerInPreviewModeProvider = koinInject<PlayerInPreviewModeProvider>()

    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val karooConnected by karooSystemServiceProvider.connectionState.collectAsStateWithLifecycle()

    var welcomeDialogVisible by remember { mutableStateOf(false) }

    var token by remember { mutableStateOf<TokenResponse?>(null) }
    var settingsInitialized by remember { mutableStateOf(false) }
    var downloadThumbnails by remember { mutableStateOf(false) }
    var highResThumbnails by remember { mutableStateOf(false) }
    val connectionState by localClient.connectionState.collectAsStateWithLifecycle(initialValue = LocalClientConnectionState.Idle)
    var enableLocalSpotify by remember { mutableStateOf(false) }
    var localSpotifyIsInstalled by remember { mutableStateOf(false) }
    var playerPreviewDialogVisible by remember { mutableStateOf(false) }
    var onlyRefreshOnActivePage by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true){
            delay(30_000L)
        }
    }

    suspend fun updateSettings(){
        karooSystemServiceProvider.saveSettings { settings ->
            settings.copy(welcomeDialogAccepted = true,
                downloadThumbnailsViaCompanion = downloadThumbnails,
                useLocalSpotifyIfAvailable = enableLocalSpotify,
                highResThumbnails = highResThumbnails,
                onlyRefreshOnActivePage = onlyRefreshOnActivePage
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runBlocking {
                updateSettings()
            }
        }
    }

    BackHandler {
        coroutineScope.launch {
            updateSettings()
            onFinish()
        }
    }

    val owner = LocalLifecycleOwner.current

    DisposableEffect(Unit) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                coroutineScope.launch {
                    localSpotifyIsInstalled = localClient.isInstalled()
                }
            }
        }

        val lifecycle = owner.lifecycle
        lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
    }

    LaunchedEffect(Unit) {
        karooSystemServiceProvider.streamSettings().collect { settings ->
            welcomeDialogVisible = !settings.welcomeDialogAccepted
            token = settings.token
            settingsInitialized = true
            downloadThumbnails = settings.downloadThumbnailsViaCompanion
            highResThumbnails = settings.highResThumbnails
            enableLocalSpotify = settings.useLocalSpotifyIfAvailable
            onlyRefreshOnActivePage = settings.onlyRefreshOnActivePage
        }
    }

    Box(modifier = Modifier.fillMaxSize()){
        Column(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {
            TopAppBar(title = { Text("Karoo Tunes") })
            Column(modifier = Modifier
                .padding(10.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp))
            {

                if(settingsInitialized) {
                    if (token == null) {
                        Text("Please log in to Spotify to enable the app.")

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("You can then proceed to add a player data field to your data pages in your profile settings.")

                        Spacer(modifier = Modifier.height(10.dp))

                        FilledTonalButton(modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp),
                            onClick = {
                                coroutineScope.launch {
                                    oauth2Client.startAuthFlow(ctx)
                                }
                            }) {
                            Icon(Icons.Default.Person, contentDescription = "Login")
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Log in to Spotify")
                        }
                    } else {
                        Text("You are logged in to Spotify.")
                    }
                }

                if (token != null){
                    if (connectionState != LocalClientConnectionState.NotInstalled && localSpotifyIsInstalled && settingsInitialized){
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = enableLocalSpotify, onCheckedChange = {
                                enableLocalSpotify = it
                                runBlocking {
                                    updateSettings()
                                }
                            })
                            Spacer(modifier = Modifier.width(10.dp))

                            Text("Control Spotify app installed on Karoo")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = downloadThumbnails, onCheckedChange = {
                            downloadThumbnails = it
                            runBlocking {
                                updateSettings()
                            }
                        })
                        Spacer(modifier = Modifier.width(10.dp))

                        if (karooSystemServiceProvider.karooSystemService.hardwareType == HardwareType.K2){
                            Text("Download thumbnails via mobile connection")
                        } else {
                            Text("Download thumbnails via companion app")
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = highResThumbnails, onCheckedChange = {
                            highResThumbnails = it
                            runBlocking {
                                updateSettings()
                            }
                        })
                        Spacer(modifier = Modifier.width(10.dp))

                        Text("Download high-resolution thumbnails")
                    }

                    // Only show onlyRefreshOnActivePage toggle when local Spotify is not connected
                    if (!(enableLocalSpotify && connectionState != LocalClientConnectionState.NotInstalled && settingsInitialized)){
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = onlyRefreshOnActivePage == true, onCheckedChange = {
                                onlyRefreshOnActivePage = it
                                runBlocking {
                                    updateSettings()
                                }
                            })
                            Spacer(modifier = Modifier.width(10.dp))

                            Text("Only refresh player when on active page")
                        }
                    }

                }

                if (token != null && settingsInitialized) {
                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp), onClick = {
                        playerPreviewDialogVisible = true

                        playerInPreviewModeProvider.update {
                            it.copy(inPreviewMode = it.inPreviewMode + 1)
                        }
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Preview")
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Preview Player")
                    }

                    FilledTonalButton(modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp), onClick = {
                        coroutineScope.launch {
                            karooSystemServiceProvider.saveSettings { settings ->
                                settings.copy(token = null)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Logout")
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Logout")
                    }
                }

                if (!karooConnected){
                    Text(modifier = Modifier.padding(5.dp), text = "Could not read device status. This app is supposed to be run on a Karoo bike computer.")
                }

                Log.d(KarooTunesExtension.TAG, "Connection state: $connectionState, localSpotifyIsInstalled: $localSpotifyIsInstalled, settingsInitialized: $settingsInitialized")

                if (localSpotifyIsInstalled && settingsInitialized && enableLocalSpotify){
                    when (connectionState){
                        is LocalClientConnectionState.Connecting -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Trying to connect to local Spotify client...")
                        }
                        is LocalClientConnectionState.Connected -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Connected to local Spotify client.")
                        }
                        is LocalClientConnectionState.Failed -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Local Spotify connection failed: ${(connectionState as LocalClientConnectionState.Failed).message}")
                        }
                        is LocalClientConnectionState.NotInstalled -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Local Spotify app is not installed.")
                        }
                        is LocalClientConnectionState.Idle -> {
                            Text(modifier = Modifier.padding(5.dp), text = "Local Spotify client is idle.")
                        }
                    }
                }

                Spacer(modifier = Modifier.padding(30.dp))
            }
         }

        Image(
            painter = painterResource(id = R.drawable.back),
            contentDescription = "Back",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = 10.dp)
                .size(54.dp)
                .clickable {
                    onFinish()
                }
        )
    }

    if (welcomeDialogVisible){
        AlertDialog(onDismissRequest = { },
            confirmButton = { Button(onClick = {
                coroutineScope.launch {
                    karooSystemServiceProvider.saveSettings { settings -> settings.copy(welcomeDialogAccepted = true) }
                }
            }) { Text("OK") } },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Welcome to Karoo Tunes!")

                    Spacer(Modifier.padding(10.dp))

                    Text("Please note that this app requires an active internet connection during riding to control the Spotify app on your phone. To use it offline, please sideload the Spotify app on your Karoo.")
                }
            }
        )
    }

    if (playerPreviewDialogVisible){
        val services = koinInject<KarooTunesServices>()

        LaunchedEffect(Unit) {
            delay(500)
            if (!karooConnected){
                Log.i(KarooTunesExtension.TAG, "Player preview dialog opened (no Karoo)")
                services.startJobs()
            }
        }

        Dialog(onDismissRequest = {
            playerPreviewDialogVisible = false
            playerInPreviewModeProvider.update {
                it.copy(inPreviewMode = it.inPreviewMode - 1)
            }
        }, properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true, usePlatformDefaultWidth = false)) {
            // Preview the field at the device's actual screen resolution (not a hardcoded
            // 480x800dp) - on a high-density Karoo, 480dp/800dp is far larger than the real
            // screen, which made the previewed artwork balloon past the visible area.
            val density = LocalDensity.current
            val displayMetrics = ctx.resources.displayMetrics
            val fullSizePx = Pair(displayMetrics.widthPixels, displayMetrics.heightPixels)

            // Tap the size label (top-right of the dialog) to cycle through PlayerSize, so
            // MEDIUM/SMALL/SINGLE_FIELD layouts can be checked here without a real Karoo profile
            // allocating the data field at that grid size.
            var previewPlayerSize by remember { mutableStateOf(PlayerSize.FULL_PAGE) }
            val heightFraction = when (previewPlayerSize) {
                PlayerSize.FULL_PAGE -> 1f
                PlayerSize.MEDIUM -> 0.4f
                // Above the SMALL layout's 190dp minimum-height threshold in PlayerViewProvider,
                // so this cycle option actually previews the Small design rather than always
                // falling back to Single Field.
                PlayerSize.SMALL -> 0.55f
                PlayerSize.SINGLE_FIELD -> 0.12f
            }
            val fieldHeightPx = (fullSizePx.second * heightFraction).roundToInt()
            val previewSizePx = Pair(fullSizePx.first, fieldHeightPx)
            val previewWidthDp = with(density) { previewSizePx.first.toDp() }
            val previewHeightDp = with(density) { previewSizePx.second.toDp() }
            // Outer dialog surface always spans the full screen height (regardless of the
            // fraction above) so the size-cycle label stays in a fixed, reliably-tappable spot
            // and shrinking the previewed field can't put taps outside the dialog's own bounds
            // (which would otherwise trigger dismissOnClickOutside).
            val dialogHeightDp = with(density) { fullSizePx.second.toDp() }

            Box(modifier = Modifier.padding(1.dp).height(dialogHeightDp).fillMaxWidth()) {
                Column(modifier = Modifier.align(Alignment.Center)) {
                    val view: RemoteViews? by viewProvider.provideView(previewPlayerSize, previewSizePx).collectAsStateWithLifecycle(initialValue = null)

                    AndroidView(
                        factory = { context ->
                            FrameLayout(context).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    previewSizePx.first,
                                    previewSizePx.second
                                )

                                val addedView = view?.apply(context, this)
                                addedView?.let { v ->
                                    addView(v)
                                }
                            }
                        },
                        update = { frameLayout ->
                            frameLayout.removeAllViews()
                            val newView = view?.apply(frameLayout.context, frameLayout)
                            newView?.let { v -> frameLayout.addView(v) }
                        },
                        modifier = Modifier
                            .size(previewWidthDp, previewHeightDp) // Force the AndroidView to this exact size
                            .background(if (isSystemInDarkTheme()) Color.Black else Color.White)
                    )
                }

                Text(
                    previewPlayerSize.name,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable {
                            val sizes = PlayerSize.entries
                            previewPlayerSize = sizes[(sizes.indexOf(previewPlayerSize) + 1) % sizes.size]
                        }
                        .padding(8.dp)
                )

                Image(
                    painter = painterResource(id = R.drawable.back),
                    contentDescription = "Back",
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 10.dp)
                        .size(54.dp)
                        .clickable {
                            playerPreviewDialogVisible = false
                            playerInPreviewModeProvider.update {
                                it.copy(inPreviewMode = it.inPreviewMode - 1)
                            }
                        }
                )
            }
        }
    }
}