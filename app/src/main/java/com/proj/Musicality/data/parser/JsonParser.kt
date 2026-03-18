package com.proj.Musicality.data.parser

import kotlinx.serialization.json.Json

object JsonParser {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
}
