package com.doughindson.karootunes.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Widget-only state that isn't about playback itself. */
data class WidgetUiState(
    /** Commands sent but not yet answered; repeat taps of these are ignored. */
    val pending: Set<PlayerCommand> = emptySet(),
)

class WidgetUiStateHolder {
    private val _state = MutableStateFlow(WidgetUiState())
    val state: StateFlow<WidgetUiState> = _state.asStateFlow()

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
}
