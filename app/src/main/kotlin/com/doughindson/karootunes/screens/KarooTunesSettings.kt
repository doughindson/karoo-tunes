package com.doughindson.karootunes.screens

import com.doughindson.karootunes.AutoVolumeConfig
import com.doughindson.karootunes.auth.TokenResponse
import com.doughindson.karootunes.jsonWithUnknownKeys
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class KarooTunesSettings(
    val welcomeDialogAccepted: Boolean = false,
    val token: TokenResponse? = null,
    val downloadThumbnailsViaCompanion: Boolean = true,
    val highResThumbnails: Boolean = false,
    val useLocalSpotifyIfAvailable: Boolean = true,
    val onlyRefreshOnActivePage: Boolean = true,
    val autoVolumeConfig: AutoVolumeConfig = AutoVolumeConfig()
){
    companion object {
        val defaultSettings = jsonWithUnknownKeys.encodeToString(KarooTunesSettings())
    }
}