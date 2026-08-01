package com.doughindson.karootunes.spotify.model

import kotlinx.serialization.Serializable

@Serializable
data class Offset(
    val position: Int? = null,
    val uri: String? = null
)