package com.doughindson.karootunes.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class ExternalUrls(
    val spotify: String? = null
)