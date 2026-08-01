package com.doughindson.karootunes.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class ExternalIds(
    val isrc: String
)

