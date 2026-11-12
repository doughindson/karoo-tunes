package com.doughindson.karootunes.ui.home

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doughindson.karootunes.R
import com.doughindson.karootunes.ui.components.BackButton
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenLibrary: () -> Unit,
    onBack: () -> Unit,
    // Activity-scoped: MainActivity hands the login redirect to this same instance.
    viewModel: HomeViewModel = koinViewModel(viewModelStoreOwner = LocalActivity.current as ComponentActivity),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val loggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val karooConnected by viewModel.karooConnected.collectAsStateWithLifecycle()
    val login by viewModel.login.collectAsStateWithLifecycle()
    var previewOpen by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val current = settings
                if (current == null || loggedIn == null) return@Column

                if (login == LoginState.InProgress) {
                    Text(stringResource(R.string.login_in_progress))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else if (loggedIn == false) {
                    Text(stringResource(R.string.home_login_prompt), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.home_login_next_step))
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                val url = viewModel.beginLogin()
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_login))
                    }
                } else {
                    Text(stringResource(R.string.home_logged_in), style = MaterialTheme.typography.bodyLarge)

                    SettingSwitch(
                        label = stringResource(if (viewModel.isKaroo2) R.string.setting_thumbnails_mobile else R.string.setting_thumbnails_companion),
                        checked = current.downloadThumbnailsViaCompanion,
                        onChange = { value -> viewModel.update { it.copy(downloadThumbnailsViaCompanion = value) } },
                    )
                    SettingSwitch(
                        label = stringResource(R.string.setting_high_res),
                        checked = current.highResThumbnails,
                        onChange = { value -> viewModel.update { it.copy(highResThumbnails = value) } },
                    )
                    SettingSwitch(
                        label = stringResource(R.string.setting_only_active_page),
                        checked = current.onlyRefreshOnActivePage,
                        onChange = { value -> viewModel.update { it.copy(onlyRefreshOnActivePage = value) } },
                    )

                    FilledTonalButton(onClick = { previewOpen = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_preview))
                    }
                    FilledTonalButton(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(painterResource(R.drawable.ic_library_music), contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_library))
                    }
                    OutlinedButton(onClick = { viewModel.logout() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_logout))
                    }
                }

                if (!karooConnected) {
                    Text(stringResource(R.string.home_not_on_karoo), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(72.dp)) // keep content clear of the back button
            }
            BackButton(onBack)
        }
    }

    val current = settings
    if (current != null && !current.welcomeDialogAccepted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.welcome_title)) },
            text = { Text(stringResource(R.string.welcome_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.update { it.copy(welcomeDialogAccepted = true) } }) { Text(stringResource(R.string.ok)) }
            },
        )
    }

    (login as? LoginState.Failed)?.let { failed ->
        AlertDialog(
            onDismissRequest = viewModel::dismissLoginError,
            text = { Text(stringResource(R.string.login_failed, failed.message)) },
            confirmButton = { TextButton(onClick = viewModel::dismissLoginError) { Text(stringResource(R.string.ok)) } },
        )
    }

    if (previewOpen) PlayerPreviewDialog(onDismiss = { previewOpen = false })
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
