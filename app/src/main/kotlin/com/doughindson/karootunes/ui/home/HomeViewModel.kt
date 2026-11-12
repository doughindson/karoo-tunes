package com.doughindson.karootunes.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doughindson.karootunes.data.auth.SpotifyAuth
import com.doughindson.karootunes.data.karoo.KarooGateway
import com.doughindson.karootunes.data.settings.Settings
import com.doughindson.karootunes.data.settings.SettingsRepository
import io.hammerhead.karooext.models.HardwareType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

sealed interface LoginState {
    data object Idle : LoginState
    data object InProgress : LoginState
    data class Failed(val message: String) : LoginState
}

class HomeViewModel(
    private val settingsRepository: SettingsRepository,
    private val auth: SpotifyAuth,
    private val karoo: KarooGateway,
) : ViewModel() {
    val settings: StateFlow<Settings?> = settingsRepository.state
    val isLoggedIn: StateFlow<Boolean?> = auth.isLoggedIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val karooConnected: StateFlow<Boolean> = karoo.connected

    private val _login = MutableStateFlow<LoginState>(LoginState.Idle)
    val login: StateFlow<LoginState> = _login.asStateFlow()

    val isKaroo2: Boolean get() = karoo.hardwareType == HardwareType.K2

    /** Returns the URL to open in the browser. */
    suspend fun beginLogin(): String = auth.beginAuthorization()

    fun completeLogin(code: String?, state: String?, error: String?) {
        _login.value = LoginState.InProgress
        viewModelScope.launch {
            _login.value = try {
                auth.completeAuthorization(code, state, error)
                LoginState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoginState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun dismissLoginError() {
        _login.value = LoginState.Idle
    }

    fun logout() = viewModelScope.launch { auth.logout() }

    fun update(transform: (Settings) -> Settings) = viewModelScope.launch { settingsRepository.update(transform) }
}
