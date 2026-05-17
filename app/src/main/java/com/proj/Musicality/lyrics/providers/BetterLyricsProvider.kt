package com.proj.Musicality.lyrics.providers

import com.proj.Musicality.lyrics.LyricsProvider
import com.proj.Musicality.lyrics.TTMLParser
import com.proj.Musicality.lyrics.lyricsHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

@Serializable
private data class TTMLResponse(val ttml: String? = null)

object BetterLyricsProvider : LyricsProvider {
    override val name: String = "BetterLyrics"

    private const val BASE = "https://lyrics-api.boidu.dev"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = runCatching {
        val response = lyricsHttpClient.get("$BASE/getLyrics") {
            // Upstream gates uncached lookups behind an API key UNLESS the request
            // claims to come from music.youtube.com. Without these headers every
            // uncached song returns HTTP 401 ("Uncached queries require ... X-API-Key").
            header("Origin", "https://music.youtube.com")
            header("Referer", "https://music.youtube.com/")
            header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1")
            header("Accept", "*/*")
            parameter("s", title)
            parameter("a", artist)
            if (duration > 0) parameter("d", duration)
            if (!album.isNullOrBlank()) parameter("al", album)
        }
        if (!response.status.isSuccess()) throw IllegalStateException("HTTP ${response.status}")
        val ttml = response.body<TTMLResponse>().ttml?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("No TTML in response")
        val parsed = TTMLParser.parseTTML(ttml)
        if (parsed.isEmpty()) throw IllegalStateException("Failed to parse TTML")
        TTMLParser.toLRC(parsed)
    }
}
