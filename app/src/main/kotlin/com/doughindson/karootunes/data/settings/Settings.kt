package com.doughindson.karootunes.data.settings

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Settings(
    val welcomeDialogAccepted: Boolean = false,
    val downloadThumbnailsViaCompanion: Boolean = true,
    val highResThumbnails: Boolean = false,
    /** Only poll Spotify while the player is on the visible ride page (otherwise: whenever it's in the active profile). */
    val onlyRefreshOnActivePage: Boolean = true,
    /** Tokens used to live in the settings blob. Read once to migrate them to [com.doughindson.karootunes.data.auth.TokenStore]. */
    @SerialName("token") val legacyToken: JsonElement? = null,
)
