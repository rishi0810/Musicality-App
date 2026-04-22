package com.proj.Musicality.lyrics

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Shared Ktor client for all lyrics providers. Uses the OkHttp engine so we don't
 * pull in a second HTTP stack on top of the one Coil/Coil-network-okhttp already uses.
 */
internal val lyricsJson = Json {
    isLenient = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal val lyricsHttpClient: HttpClient by lazy {
    HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(lyricsJson)
            // Some providers (KuGou, others) serve JSON with text/plain or text/html content-types.
            json(lyricsJson, ContentType.Text.Html)
            json(lyricsJson, ContentType.Text.Plain)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
    }
}
