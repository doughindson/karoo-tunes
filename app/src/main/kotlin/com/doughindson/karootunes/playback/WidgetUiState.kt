package com.doughindson.karootunes.playback

import com.doughindson.karootunes.AppScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Widget-only state that isn't about playback itself. */
data class WidgetUiState(
    val optionsOpen: Boolean = false,
    /** Commands sent but not yet answered; repeat taps of these are ignored. */
    val pending: Set<PlayerCommand> = emptySet(),
)

class WidgetUiStateHolder(private val appScope: AppScope) {
    private val _state = MutableStateFlow(WidgetUiState())
    val state: StateFlow<WidgetUiState> = _state.asStateFlow()
    private var autoClose: Job? = null

    fun toggleOptions() {
        _state.update { it.copy(optionsOpen = !it.optionsOpen) }
        autoClose?.cancel()
        if (_state.value.optionsOpen) {
            autoClose = appScope.launch {
                delay(OPTIONS_TIMEOUT_MS)
                closeOptions()
            }
        }
    }

    fun closeOptions() {
        autoClose?.cancel()
        _state.update { it.copy(optionsOpen = false) }
    }

    /** Marks [command] as in flight. False if it already was, so double taps don't send it twice. */
    fun tryMarkPending(command: PlayerCommand): Boolean {
        var added = false
        _state.update { current ->
            added = command !in current.pending
            if (added) current.copy(pending = current.pending + command) else current
        }
        return added
    }

    fun clearPending(command: PlayerCommand) = _state.update { it.copy(pending = it.pending - command) }

    private companion object {
        const val OPTIONS_TIMEOUT_MS = 5_000L
    }
}
