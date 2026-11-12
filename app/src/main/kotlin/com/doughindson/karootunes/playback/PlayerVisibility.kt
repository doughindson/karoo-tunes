package com.doughindson.karootunes.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Counts open player previews (profile editor, in-app preview); the player refreshes while any is open. */
class PlayerVisibility {
    private val _previews = MutableStateFlow(0)
    val previews: StateFlow<Int> = _previews.asStateFlow()

    fun enterPreview() = _previews.update { it + 1 }
    fun exitPreview() = _previews.update { (it - 1).coerceAtLeast(0) }
}
