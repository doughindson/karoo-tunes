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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.doughindson.karootunes.R
import com.doughindson.karootunes.ui.components.BackButton
import com.doughindson.karootunes.ui.components.BottomClearance
import com.doughindson.karootunes.ui.components.PillButton
import com.doughindson.karootunes.ui.components.ScreenHeader
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

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

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ScreenHeader(stringResource(R.string.app_name))
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val current = settings
                if (current == null || loggedIn == null) return@Column

                when {
                    login == LoginState.InProgress -> {
                        Text(stringResource(R.string.login_in_progress), style = MaterialTheme.typography.bodyLarge)
                        LinearProgressIndicator(
                            Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                    loggedIn == false -> {
                        Text(stringResource(R.string.home_login_prompt), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.home_login_next_step),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        PillButton(
                            stringResource(R.string.home_login),
                            onClick = {
                                scope.launch {
                                    val url = viewModel.beginLogin()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                }
                            },
                            primary = true,
                            icon = R.drawable.spotify_icon_black,
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.home_logged_in),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        PillButton(stringResource(R.string.home_library), onOpenLibrary, primary = true, icon = R.drawable.ic_library_music)
                        PillButton(stringResource(R.string.home_preview), { previewOpen = true }, icon = R.drawable.ic_play_arrow)

                        Text(
                            stringResource(R.string.settings),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        SettingRow(
                            label = stringResource(if (viewModel.isKaroo2) R.string.setting_thumbnails_mobile else R.string.setting_thumbnails_companion),
                            checked = current.downloadThumbnailsViaCompanion,
                            onChange = { value -> viewModel.update { it.copy(downloadThumbnailsViaCompanion = value) } },
                        )
                        SettingRow(
                            label = stringResource(R.string.setting_high_res),
                            checked = current.highResThumbnails,
                            onChange = { value -> viewModel.update { it.copy(highResThumbnails = value) } },
                        )
                        SettingRow(
                            label = stringResource(R.string.setting_only_active_page),
                            checked = current.onlyRefreshOnActivePage,
                            onChange = { value -> viewModel.update { it.copy(onlyRefreshOnActivePage = value) } },
                        )
                        TextButton(
                            onClick = { viewModel.logout() },
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) {
                            Text(
                                stringResource(R.string.home_logout),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (!karooConnected) {
                    Text(
                        stringResource(R.string.home_not_on_karoo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(BottomClearance))
        }
        BackButton(onBack)
    }

    val current = settings
    if (current != null && !current.welcomeDialogAccepted) {
        AppDialog(
            title = stringResource(R.string.welcome_title),
            text = stringResource(R.string.welcome_body),
            onConfirm = { viewModel.update { it.copy(welcomeDialogAccepted = true) } },
            onDismiss = {},
        )
    }

    (login as? LoginState.Failed)?.let { failed ->
        AppDialog(
            title = null,
            text = stringResource(R.string.login_failed, failed.message),
            onConfirm = viewModel::dismissLoginError,
            onDismiss = viewModel::dismissLoginError,
        )
    }

    if (previewOpen) PlayerPreviewDialog(onDismiss = { previewOpen = false })
}

/** Label on the left (up to two lines), switch on the right; the whole row toggles. */
@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun AppDialog(title: String?, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        title = title?.let { { Text(it, style = MaterialTheme.typography.titleLarge) } },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(onClick = onConfirm, shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.ok), style = MaterialTheme.typography.labelLarge)
            }
        },
    )
}
