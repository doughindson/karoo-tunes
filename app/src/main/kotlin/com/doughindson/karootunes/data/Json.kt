package com.doughindson.karootunes.data

import kotlinx.serialization.json.Json

/** Lenient JSON for Spotify responses and stored data: unknown fields are ignored, missing ones defaulted. */
val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}
