package com.doughindson.karootunes.spotify

import android.util.Log
import com.doughindson.karootunes.KarooTunesExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PlayerInPreviewModeProvider {
    data class PlayerInPreviewModeState(val inPreviewMode: Int = 0)

    private val _state = MutableStateFlow(PlayerInPreviewModeState())

    val state = _state.asStateFlow()

    fun update(function: (PlayerInPreviewModeState) -> PlayerInPreviewModeState) {
        _state.update {
            val newState = function(it)

            Log.d(KarooTunesExtension.TAG, "Updating PlayerInPreviewModeState: ${newState.inPreviewMode}")

            newState
        }
    }
}