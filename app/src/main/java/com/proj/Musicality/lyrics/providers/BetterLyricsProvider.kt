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
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            header("Accept", "application/json")
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
